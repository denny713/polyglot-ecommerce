package token

import (
	"crypto/rsa"
	"encoding/base64"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

// keyServer serves a body a test can swap, and counts how often it was read.
type keyServer struct {
	*httptest.Server

	body   atomic.Value
	status atomic.Int64
	hits   atomic.Int64
}

func newKeyServer(t *testing.T, body []byte) *keyServer {
	t.Helper()

	server := &keyServer{}
	server.body.Store(body)
	server.status.Store(http.StatusOK)

	server.Server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		server.hits.Add(1)

		require.Equal(t, "/protocol/openid-connect/certs", r.URL.Path,
			"the keys are read from the realm's certs endpoint")

		status := int(server.status.Load())
		if status != http.StatusOK {
			w.WriteHeader(status)

			return
		}

		_, _ = w.Write(server.body.Load().([]byte))
	}))
	t.Cleanup(server.Close)

	return server
}

// source builds a key source reading from the server, with a clock a test drives.
func (s *keyServer) source(now func() time.Time) *jwks {
	built := NewKeySource(s.URL, s.Client()).(*jwks)
	if now != nil {
		built.now = now
	}

	return built
}

func TestKeySourceReadsTheRealmOnce(t *testing.T) {
	r := newRealm(t, testKid)
	server := newKeyServer(t, r.jwksJSON("sig"))
	keys := server.source(nil)

	first, err := keys.Key(testKid)
	require.NoError(t, err)
	require.Equal(t, r.key.N, first.N)
	require.Equal(t, r.key.E, first.E)

	// The keys are kept, a second token signed by the same key costs no request.
	second, err := keys.Key(testKid)
	require.NoError(t, err)
	require.Same(t, first, second)
	require.Equal(t, int64(1), server.hits.Load())
}

func TestKeySourceRefetchesForARotatedKey(t *testing.T) {
	first := newRealm(t, "key-one")
	server := newKeyServer(t, first.jwksJSON("sig"))

	clock := time.Now()
	keys := server.source(func() time.Time { return clock })

	_, err := keys.Key("key-one")
	require.NoError(t, err)

	// The realm rotates its key. The kid on the next token is unknown, so the
	// key set is read again rather than the token being refused.
	rotated := newRealm(t, "key-two")
	server.body.Store(rotated.jwksJSON("sig"))
	clock = clock.Add(refreshInterval)

	key, err := keys.Key("key-two")
	require.NoError(t, err)
	require.Equal(t, rotated.key.N, key.N)
	require.Equal(t, int64(2), server.hits.Load())
}

func TestKeySourceDoesNotRefetchForEveryUnknownKey(t *testing.T) {
	r := newRealm(t, testKid)
	server := newKeyServer(t, r.jwksJSON("sig"))

	clock := time.Now()
	keys := server.source(func() time.Time { return clock })

	_, err := keys.Key(testKid)
	require.NoError(t, err)
	require.Equal(t, int64(1), server.hits.Load())

	// A caller inventing kids must not drive one request to the realm per request
	// it makes, so within the interval an unknown kid is refused from what is
	// already held.
	for range 20 {
		_, err = keys.Key("made-up")
		require.Error(t, err)
	}

	require.Equal(t, int64(1), server.hits.Load())

	// Once the interval has passed it is worth looking again.
	clock = clock.Add(refreshInterval)
	_, err = keys.Key("made-up")
	require.Error(t, err)
	require.Equal(t, int64(2), server.hits.Load())
}

func TestKeySourceWhenTheRealmFails(t *testing.T) {
	r := newRealm(t, testKid)
	server := newKeyServer(t, r.jwksJSON("sig"))
	server.status.Store(http.StatusServiceUnavailable)

	keys := server.source(nil)

	_, err := keys.Key(testKid)
	require.ErrorContains(t, err, "fetch the key set")

	// Nothing was cached, so the next attempt reads the realm again and recovers
	// once it answers.
	server.status.Store(http.StatusOK)

	key, err := keys.Key(testKid)
	require.NoError(t, err)
	require.Equal(t, r.key.N, key.N)
}

func TestKeySourceWhenTheRealmIsUnreachable(t *testing.T) {
	server := newKeyServer(t, nil)
	keys := server.source(nil)
	server.Close()

	_, err := keys.Key(testKid)

	require.ErrorContains(t, err, "fetch the key set")
}

func TestKeySourceWithAnUnusableDocument(t *testing.T) {
	r := newRealm(t, testKid)
	modulus := base64.RawURLEncoding.EncodeToString(r.key.N.Bytes())

	tests := []struct {
		name string
		body string
		want string
	}{
		{name: "not json", body: `{`, want: "decode the key set"},
		{name: "no keys", body: `{"keys":[]}`, want: "holds no RS256 signing key"},
		{
			name: "only an encryption key",
			body: `{"keys":[{"kty":"RSA","kid":"enc","use":"enc","n":"` + modulus + `","e":"AQAB"}]}`,
			want: "holds no RS256 signing key",
		},
		{
			name: "only an elliptic curve key",
			body: `{"keys":[{"kty":"EC","kid":"ec","use":"sig","n":"` + modulus + `","e":"AQAB"}]}`,
			want: "holds no RS256 signing key",
		},
		{
			name: "another signing algorithm",
			body: `{"keys":[{"kty":"RSA","kid":"ps","alg":"PS256","use":"sig","n":"` + modulus + `","e":"AQAB"}]}`,
			want: "holds no RS256 signing key",
		},
		{
			name: "modulus is not base64url",
			body: `{"keys":[{"kty":"RSA","kid":"bad","use":"sig","n":"!!!","e":"AQAB"}]}`,
			want: "modulus",
		},
		{
			name: "exponent is not base64url",
			body: `{"keys":[{"kty":"RSA","kid":"bad","use":"sig","n":"` + modulus + `","e":"!!!"}]}`,
			want: "exponent",
		},
		{
			name: "modulus is empty",
			body: `{"keys":[{"kty":"RSA","kid":"bad","use":"sig","n":"","e":"AQAB"}]}`,
			want: "modulus and exponent must both be set",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			server := newKeyServer(t, []byte(test.body))

			_, err := server.source(nil).Key(testKid)

			require.ErrorContains(t, err, test.want)
		})
	}
}

func TestKeySourceAcceptsAKeyWithoutAUse(t *testing.T) {
	// Keycloak sets use, but the field is optional in a JWKS. A key that does not
	// say what it is for is still usable for signatures.
	r := newRealm(t, testKid)
	server := newKeyServer(t, r.jwksJSON(""))

	key, err := server.source(nil).Key(testKid)

	require.NoError(t, err)
	require.Equal(t, r.key.N, key.N)
}

func TestNewKeySourceHasAClientOfItsOwn(t *testing.T) {
	// Nothing is passed in at start up, so the source has to bring a client with
	// a timeout rather than borrow http.DefaultClient, which has none.
	built := NewKeySource("http://localhost:8080/realms/ecommerce", nil).(*jwks)

	require.Equal(t, "http://localhost:8080/realms/ecommerce/protocol/openid-connect/certs", built.url)
	require.NotNil(t, built.client)
	require.NotSame(t, http.DefaultClient, built.client)
	require.Positive(t, built.client.Timeout)
}

func TestParseRSAKey(t *testing.T) {
	r := newRealm(t, testKid)

	key, err := parseRSAKey(
		base64.RawURLEncoding.EncodeToString(r.key.N.Bytes()),
		base64.RawURLEncoding.EncodeToString([]byte{0x01, 0x00, 0x01}),
	)

	require.NoError(t, err)
	require.Equal(t, r.key.N, key.N)
	require.Equal(t, 65537, key.E)
	require.IsType(t, &rsa.PublicKey{}, key)
}
