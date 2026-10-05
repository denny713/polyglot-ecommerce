package token

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"product-service/internal/constant"
	"product-service/internal/exception"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
)

const (
	testIssuer = "http://localhost:8080/realms/ecommerce"
	testKid    = "test-key"
)

var subject = uuid.MustParse("11111111-2222-3333-4444-555555555555")

// realm is a signing key together with the JWKS a realm would publish for it.
type realm struct {
	key *rsa.PrivateKey
	kid string
}

func newRealm(t *testing.T, kid string) realm {
	t.Helper()

	key, err := rsa.GenerateKey(rand.Reader, 2048)
	require.NoError(t, err)

	return realm{key: key, kid: kid}
}

// sign mints a token, letting a test override any claim of an otherwise valid
// one.
func (r realm) sign(t *testing.T, claims jwt.MapClaims) string {
	t.Helper()

	base := jwt.MapClaims{
		"sub": subject.String(),
		"iss": testIssuer,
		"exp": time.Now().Add(time.Hour).Unix(),
		"realm_access": map[string]any{
			"roles": []any{"offline_access", constant.AdminRole},
		},
	}

	for name, value := range claims {
		if value == nil {
			delete(base, name)

			continue
		}

		base[name] = value
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, base)
	token.Header["kid"] = r.kid

	signed, err := token.SignedString(r.key)
	require.NoError(t, err)

	return signed
}

// jwksJSON is the document the realm's certs endpoint would answer with.
func (r realm) jwksJSON(use string) []byte {
	key := map[string]any{
		"kty": "RSA",
		"kid": r.kid,
		"alg": "RS256",
		"n":   base64.RawURLEncoding.EncodeToString(r.key.N.Bytes()),
		"e":   base64.RawURLEncoding.EncodeToString(big.NewInt(int64(r.key.E)).Bytes()),
	}
	if use != "" {
		key["use"] = use
	}

	document, _ := json.Marshal(map[string]any{"keys": []any{key}})

	return document
}

// keySourceFor serves the realm's keys and returns a source reading them.
func keySourceFor(t *testing.T, r realm) KeySource {
	t.Helper()

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write(r.jwksJSON("sig"))
	}))
	t.Cleanup(server.Close)

	return NewKeySource(server.URL, server.Client())
}

// newVerifierFor builds a verifier reading the keys of the given realm.
func newVerifierFor(t *testing.T, r realm) Verifier {
	t.Helper()

	return NewVerifier(testIssuer, keySourceFor(t, r))
}

func TestVerify(t *testing.T) {
	r := newRealm(t, testKid)

	claims, err := newVerifierFor(t, r).Verify(r.sign(t, nil))

	require.NoError(t, err)
	require.Equal(t, subject, claims.Subject)
	require.Equal(t, []string{"offline_access", constant.AdminRole}, claims.Roles)
	require.True(t, claims.HasAnyRole(constant.AdminRole))
}

func TestVerifyAToken(t *testing.T) {
	r := newRealm(t, testKid)
	verifier := newVerifierFor(t, r)

	tests := []struct {
		name   string
		claims jwt.MapClaims
		want   *exception.Exception
	}{
		{name: "expired", claims: jwt.MapClaims{"exp": time.Now().Add(-time.Minute).Unix()},
			want: exception.ErrTokenRejected},
		{name: "no expiry", claims: jwt.MapClaims{"exp": nil},
			want: exception.ErrTokenRejected},
		{name: "another issuer", claims: jwt.MapClaims{"iss": "http://localhost:8080/realms/other"},
			want: exception.ErrTokenRejected},
		{name: "no issuer", claims: jwt.MapClaims{"iss": nil},
			want: exception.ErrTokenRejected},
		{name: "no subject", claims: jwt.MapClaims{"sub": nil},
			want: exception.ErrTokenRejected},
		{name: "subject is not an id", claims: jwt.MapClaims{"sub": "not-a-uuid"},
			want: exception.ErrTokenMalformed},
		{name: "not yet valid", claims: jwt.MapClaims{"nbf": time.Now().Add(time.Hour).Unix()},
			want: exception.ErrTokenRejected},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := verifier.Verify(r.sign(t, test.claims))

			require.ErrorIs(t, err, test.want)
		})
	}
}

func TestVerifyAMalformedToken(t *testing.T) {
	verifier := newVerifierFor(t, newRealm(t, testKid))

	for name, raw := range map[string]string{
		"empty":        "",
		"one segment":  "abc",
		"two segments": "abc.def",
		"not base64":   "!!!.!!!.!!!",
	} {
		t.Run(name, func(t *testing.T) {
			_, err := verifier.Verify(raw)

			require.ErrorIs(t, err, exception.ErrTokenMalformed)
		})
	}
}

func TestVerifyRejectsAnotherAlgorithm(t *testing.T) {
	r := newRealm(t, testKid)
	verifier := newVerifierFor(t, r)

	// A token the caller signed with a secret of its own must not be accepted
	// just because its header asks for HS256.
	forged := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"sub": subject.String(),
		"iss": testIssuer,
		"exp": time.Now().Add(time.Hour).Unix(),
		"realm_access": map[string]any{
			"roles": []any{constant.AdminRole},
		},
	})
	forged.Header["kid"] = r.kid

	raw, err := forged.SignedString([]byte("a secret the realm never issued"))
	require.NoError(t, err)

	_, err = verifier.Verify(raw)

	require.ErrorIs(t, err, exception.ErrTokenRejected)
}

func TestVerifyRejectsAnUnsignedToken(t *testing.T) {
	verifier := newVerifierFor(t, newRealm(t, testKid))

	unsigned := jwt.NewWithClaims(jwt.SigningMethodNone, jwt.MapClaims{
		"sub": subject.String(),
		"iss": testIssuer,
		"exp": time.Now().Add(time.Hour).Unix(),
	})

	raw, err := unsigned.SignedString(jwt.UnsafeAllowNoneSignatureType)
	require.NoError(t, err)

	_, err = verifier.Verify(raw)

	require.ErrorIs(t, err, exception.ErrTokenRejected)
}

func TestVerifyRejectsAForeignSigningKey(t *testing.T) {
	realmKey := newRealm(t, testKid)

	// The token is well formed and claims the right kid, but it was signed by a
	// key this realm never published.
	foreign := realm{key: newRealm(t, testKid).key, kid: testKid}

	_, err := newVerifierFor(t, realmKey).Verify(foreign.sign(t, nil))

	require.ErrorIs(t, err, exception.ErrTokenRejected)
}

func TestVerifyWhenTheKeySetCannotBeRead(t *testing.T) {
	r := newRealm(t, testKid)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()

	verifier := NewVerifier(testIssuer, NewKeySource(server.URL, server.Client()))

	_, err := verifier.Verify(r.sign(t, nil))

	// A realm this service could not reach is its own failure, so the caller is
	// told to retry rather than to log in again.
	require.ErrorIs(t, err, exception.ErrTokenUnverifiable)
}

func TestVerifyRolesOfAToken(t *testing.T) {
	r := newRealm(t, testKid)
	verifier := newVerifierFor(t, r)

	tests := []struct {
		name   string
		claims jwt.MapClaims
		want   []string
	}{
		{name: "no realm_access", claims: jwt.MapClaims{"realm_access": nil}},
		{name: "realm_access is not an object", claims: jwt.MapClaims{"realm_access": "admin"}},
		{name: "no roles key", claims: jwt.MapClaims{"realm_access": map[string]any{}}},
		{name: "roles is not a list", claims: jwt.MapClaims{"realm_access": map[string]any{"roles": "admin"}}},
		{
			name:   "roles hold a value that is not a name",
			claims: jwt.MapClaims{"realm_access": map[string]any{"roles": []any{constant.UserRole, 7}}},
			want:   []string{constant.UserRole},
		},
		{
			name:   "client roles are not realm roles",
			claims: jwt.MapClaims{"resource_access": map[string]any{"roles": []any{constant.AdminRole}}, "realm_access": nil},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			claims, err := verifier.Verify(r.sign(t, test.claims))

			// A token whose roles cannot be read carries none, which the guard
			// then refuses. It is never mistaken for an administrator.
			require.NoError(t, err)
			require.Equal(t, test.want, claims.Roles)
			require.False(t, claims.HasAnyRole(constant.AdminRole))
		})
	}
}

func TestVerifyRejectsATokenNamingAKeyTheRealmDoesNotPublish(t *testing.T) {
	published := newRealm(t, "the-published-key")
	server := newKeyServer(t, published.jwksJSON("sig"))

	// The token is signed by a key of its own and names a kid the realm never
	// published, which is a token from somewhere else rather than a realm this
	// service failed to read.
	elsewhere := realm{key: newRealm(t, "another-key").key, kid: "another-key"}

	verifier := NewVerifier(testIssuer, server.source(nil))

	_, err := verifier.Verify(elsewhere.sign(t, nil))

	require.ErrorIs(t, err, exception.ErrTokenRejected)
	require.NotErrorIs(t, err, exception.ErrTokenUnverifiable)
}
