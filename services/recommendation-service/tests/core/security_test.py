import time
from uuid import UUID

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa
from fastapi.security import HTTPAuthorizationCredentials
from jwt.algorithms import RSAAlgorithm

from core import security
from core.security import Principal, TokenError, authenticate, require_admin

ISSUER = "http://localhost:8080/realms/ecommerce"
USER = "aaaaaaaa-0000-0000-0000-000000000000"
KID = "realm-key"


def new_key():
    return rsa.generate_private_key(public_exponent=65537, key_size=2048)


REALM_KEY = new_key()
FOREIGN_KEY = new_key()

# The cached client, kept before any test replaces it with a fake
CACHED_JWKS_CLIENT = security.jwks_client


class FakeJwks:
    """The realm's JWKS endpoint, holding the public half of REALM_KEY."""

    def __init__(self, error=None):
        jwk = RSAAlgorithm.to_jwk(REALM_KEY.public_key(), as_dict=True)
        self.keys = {KID: jwt.PyJWK({**jwk, "kid": KID, "alg": "RS256", "use": "sig"})}
        self.error = error

    def get_signing_key_from_jwt(self, token):
        if self.error:
            raise self.error
        kid = jwt.get_unverified_header(token).get("kid")
        if kid not in self.keys:
            raise jwt.PyJWKClientError(f'Unable to find a signing key that matches: "{kid}"')
        return self.keys[kid]


@pytest.fixture(autouse=True)
def realm(monkeypatch):
    monkeypatch.setattr(security.settings, "KEYCLOAK_ISSUER_URI", ISSUER)
    jwks = FakeJwks()
    monkeypatch.setattr(security, "jwks_client", lambda: jwks)
    return jwks


def token(key=REALM_KEY, kid=KID, algorithm="RS256", drop=(), **overrides):
    claims = {
        "iss": ISSUER,
        "sub": USER,
        "exp": int(time.time()) + 300,
        "iat": int(time.time()),
        "realm_access": {"roles": ["user", "offline_access"]},
        **overrides,
    }
    for claim in drop:
        claims.pop(claim)
    return jwt.encode(claims, key, algorithm=algorithm, headers={"kid": kid})


def bearer(value):
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials=value)


def refused(credentials):
    with pytest.raises(TokenError) as error:
        authenticate(credentials)
    return error.value


class TestAuthenticate:

    def test_answers_the_user_and_realm_roles_of_a_valid_token(self):
        principal = authenticate(bearer(token()))

        assert principal == Principal(UUID(USER), frozenset({"user", "offline_access"}))
        assert not principal.is_admin

    def test_knows_an_admin(self):
        principal = authenticate(bearer(token(realm_access={"roles": ["admin"]})))

        assert principal.is_admin

    def test_accepts_an_issuer_configured_with_a_trailing_slash(self, monkeypatch):
        monkeypatch.setattr(security.settings, "KEYCLOAK_ISSUER_URI", ISSUER + "/")

        assert authenticate(bearer(token())).user_id == UUID(USER)

    @pytest.mark.parametrize("credentials", [None, bearer(""), bearer("   ")])
    def test_refuses_a_request_without_a_token(self, credentials):
        error = refused(credentials)

        assert (error.code, error.status) == (401, "Unauthorized")
        assert error.message == "No access token found, please login first"

    @pytest.mark.parametrize("value", ["not-a-jwt", "a.b.c"])
    def test_refuses_a_malformed_token(self, value):
        error = refused(bearer(value))

        assert (error.code, error.status, error.message) == (400, "Bad Request", "Invalid token format")

    def test_refuses_a_subject_that_is_not_a_user_id(self):
        error = refused(bearer(token(sub="service-account")))

        assert error.code == 400

    @pytest.mark.parametrize("bad_token", [
        pytest.param(lambda: token(exp=int(time.time()) - 10), id="expired"),
        pytest.param(lambda: token(iss="http://localhost:8080/realms/other"), id="other realm"),
        pytest.param(lambda: token(key=FOREIGN_KEY), id="not signed by the realm"),
        pytest.param(lambda: token(kid="unknown-key"), id="unknown key id"),
        pytest.param(lambda: token(drop=("exp",)), id="no expiry"),
        pytest.param(lambda: token(drop=("sub",)), id="no subject"),
        pytest.param(lambda: jwt.encode({"iss": ISSUER, "sub": USER, "exp": int(time.time()) + 60},
                                        "a-shared-secret-of-thirty-two-bytes!", algorithm="HS256",
                                        headers={"kid": KID}), id="not RS256"),
    ])
    def test_refuses_a_token_it_cannot_trust(self, bad_token):
        error = refused(bearer(bad_token()))

        assert (error.code, error.status) == (401, "Unauthorized")
        assert error.message == "Access token is invalid or expired, please login again"

    @pytest.mark.parametrize("realm_access", [
        {"roles": ["offline_access"]},
        {"roles": "admin"},
        {},
        None,
    ])
    def test_refuses_a_token_without_the_user_or_admin_realm_role(self, realm_access):
        error = refused(bearer(token(realm_access=realm_access)))

        assert (error.code, error.status) == (403, "Forbidden")

    def test_ignores_a_client_role_named_like_a_realm_role(self):
        error = refused(bearer(token(realm_access=None, resource_access={"ecommerce-app": {"roles": ["admin"]}})))

        assert error.code == 403

    def test_answers_a_server_error_when_the_keys_cannot_be_fetched(self, monkeypatch):
        jwks = FakeJwks(error=jwt.PyJWKClientConnectionError("connection refused"))
        monkeypatch.setattr(security, "jwks_client", lambda: jwks)

        error = refused(bearer(token()))

        assert (error.code, error.message) == (500, "Error occurred while processing token")


class TestRequireAdmin:

    def test_lets_an_admin_through(self):
        admin = Principal(UUID(USER), frozenset({"admin"}))

        assert require_admin(admin) is admin

    def test_refuses_anyone_else(self):
        with pytest.raises(TokenError) as error:
            require_admin(Principal(UUID(USER), frozenset({"user"})))

        assert error.value.code == 403


class TestTokenError:

    def test_answers_the_error_shape_of_the_other_services(self):
        body = TokenError(401, "Unauthorized", "No access token found, please login first").body()

        assert body["code"] == 401
        assert body["status"] == "Unauthorized"
        assert body["data"]["status"] == 401
        assert body["data"]["error"] == "No access token found, please login first"
        assert body["data"]["timestamp"]


class TestJwksClient:

    @pytest.fixture(autouse=True)
    def real_client(self, monkeypatch):
        # Undo the fake of the module fixture, with a cache no other test has filled
        CACHED_JWKS_CLIENT.cache_clear()
        monkeypatch.setattr(security, "jwks_client", CACHED_JWKS_CLIENT)
        yield
        CACHED_JWKS_CLIENT.cache_clear()

    def test_reads_the_keys_of_the_realm(self, monkeypatch):
        monkeypatch.setattr(security.settings, "KEYCLOAK_ISSUER_URI", ISSUER + "/")

        assert security.jwks_client().uri == f"{ISSUER}/protocol/openid-connect/certs"

    def test_is_built_once_so_the_keys_stay_cached(self):
        assert security.jwks_client() is security.jwks_client()

    def test_answers_a_server_error_when_keycloak_is_unreachable(self, monkeypatch):
        # Nothing listens on port 1, so the connection is refused at once
        monkeypatch.setattr(security.settings, "KEYCLOAK_ISSUER_URI", "http://127.0.0.1:1/realms/ecommerce")

        error = refused(bearer(token()))

        assert error.code == 500
