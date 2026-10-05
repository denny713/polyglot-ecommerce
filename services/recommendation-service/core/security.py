"""
Authenticates every request with a Keycloak access token, the way the other
services of the project do: an RS256 JWT signed by a key of the realm's JWKS, from
the configured issuer, carrying a subject and an expiry, and a realm role.
"""
import logging
from dataclasses import dataclass
from datetime import datetime
from functools import lru_cache
from uuid import UUID

import jwt
from fastapi import Depends
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from core.config import settings

log = logging.getLogger("uvicorn.error")

SECURITY_SCHEME = "bearerAuth"
ADMIN_ROLE = "admin"
USER_ROLE = "user"

# auto_error off, so a missing token answers the same body as a bad one
bearer = HTTPBearer(
    scheme_name=SECURITY_SCHEME,
    bearerFormat="JWT",
    description="Access token of the Keycloak realm, from `POST /api/auth/login` of auth-service",
    auto_error=False,
)


class TokenError(Exception):
    """A request refused before it reaches an endpoint, answered by the handler in main.py."""

    def __init__(self, code: int, status: str, message: str):
        super().__init__(message)
        self.code = code
        self.status = status
        self.message = message

    def body(self) -> dict:
        # The error shape of order-service and inventory-service
        return {
            "code": self.code,
            "status": self.status,
            "data": {"timestamp": datetime.now().isoformat(), "status": self.code, "error": self.message},
        }


def unauthorized(message: str) -> TokenError:
    return TokenError(401, "Unauthorized", message)


def forbidden() -> TokenError:
    return TokenError(403, "Forbidden", "You don't have permission to access this resource")


@dataclass(frozen=True)
class Principal:
    """The user behind the request."""

    user_id: UUID
    roles: frozenset[str]

    @property
    def is_admin(self) -> bool:
        return ADMIN_ROLE in self.roles


def issuer() -> str:
    return settings.KEYCLOAK_ISSUER_URI.rstrip("/")


@lru_cache
def jwks_client() -> jwt.PyJWKClient:
    # Keys are cached and fetched again only for a kid not seen yet, so a key
    # rotation in Keycloak is picked up without a restart
    return jwt.PyJWKClient(f"{issuer()}/protocol/openid-connect/certs", cache_keys=True)


def _realm_roles(claims: dict) -> frozenset[str]:
    # Realm roles; client roles would arrive under resource_access instead
    roles = (claims.get("realm_access") or {}).get("roles")
    return frozenset(role for role in roles if isinstance(role, str)) if isinstance(roles, list) else frozenset()


def authenticate(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)) -> Principal:
    """
    Answers who is calling, or refuses the request. A plain function on purpose:
    FastAPI runs it in a worker thread, where fetching the JWKS may block.
    """
    if credentials is None or not credentials.credentials.strip():
        log.error("Access token not found")
        raise unauthorized("No access token found, please login first")

    token = credentials.credentials.strip()
    try:
        key = jwks_client().get_signing_key_from_jwt(token).key
        claims = jwt.decode(
            token,
            key,
            algorithms=["RS256"],
            issuer=issuer(),
            options={"require": ["sub", "exp"], "verify_aud": False},
        )
        user_id = UUID(claims["sub"])
    except jwt.PyJWKClientConnectionError:
        log.exception("Unable to fetch the signing keys")
        raise TokenError(500, "Internal Server Error", "Error occurred while processing token")
    except jwt.InvalidSignatureError as e:
        # A DecodeError to PyJWT, but a well-formed token signed by the wrong key
        log.error("Rejected access token: %s", e)
        raise unauthorized("Access token is invalid or expired, please login again")
    except (jwt.DecodeError, ValueError) as e:
        log.error("Malformed access token: %s", e)
        raise TokenError(400, "Bad Request", "Invalid token format")
    except (jwt.InvalidTokenError, jwt.PyJWKClientError) as e:
        log.error("Rejected access token: %s", e)
        raise unauthorized("Access token is invalid or expired, please login again")

    roles = _realm_roles(claims)
    if not roles & {USER_ROLE, ADMIN_ROLE}:
        log.error("Access token without the '%s' or '%s' role: sub=%s", USER_ROLE, ADMIN_ROLE, user_id)
        raise forbidden()

    return Principal(user_id, roles)


def require_admin(principal: Principal = Depends(authenticate)) -> Principal:
    if not principal.is_admin:
        log.error("Access token without the '%s' role: sub=%s", ADMIN_ROLE, principal.user_id)
        raise forbidden()
    return principal
