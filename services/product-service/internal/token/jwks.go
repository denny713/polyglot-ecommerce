package token

import (
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"sync"
	"time"
)

// KeySource hands out the public key a token was signed with, looked up by the
// kid of its header.
type KeySource interface {
	Key(kid string) (*rsa.PublicKey, error)
}

// ErrUnknownKey reports a kid the realm does not publish. It is told apart from
// a key set that could not be read at all because a token signed by a key
// nobody published is the caller's problem, while an unreachable realm is this
// service's own.
var ErrUnknownKey = errors.New("the key set holds no such signing key")

// refreshInterval is the shortest gap between two fetches of the key set. A kid
// nobody published would otherwise let an unauthenticated caller drive one
// request to the identity provider per request it makes.
const refreshInterval = time.Minute

// jwks reads the signing keys from the realm's JWKS endpoint and keeps them.
// Keycloak rotates keys, so an unknown kid triggers one refetch rather than a
// rejection, and the keys are only read from the network on first use.
type jwks struct {
	url    string
	client *http.Client
	now    func() time.Time

	mu        sync.Mutex
	keys      map[string]*rsa.PublicKey
	refreshed time.Time
}

// NewKeySource builds the key source of a Keycloak realm. The issuer is the
// realm base url, the same value the tokens carry as their iss claim.
func NewKeySource(issuer string, client *http.Client) KeySource {
	if client == nil {
		client = &http.Client{Timeout: 10 * time.Second}
	}

	return &jwks{
		url:    issuer + "/protocol/openid-connect/certs",
		client: client,
		now:    time.Now,
	}
}

func (j *jwks) Key(kid string) (*rsa.PublicKey, error) {
	j.mu.Lock()
	defer j.mu.Unlock()

	if key, ok := j.keys[kid]; ok {
		return key, nil
	}

	// The key set is fetched when it was never read, and again when it holds no
	// key under this kid and the last fetch is old enough to try once more.
	if j.keys != nil && j.now().Sub(j.refreshed) < refreshInterval {
		return nil, fmt.Errorf("%w: %q", ErrUnknownKey, kid)
	}

	keys, err := j.fetch()
	if err != nil {
		return nil, err
	}

	j.keys, j.refreshed = keys, j.now()

	key, ok := keys[kid]
	if !ok {
		return nil, fmt.Errorf("%w: %q", ErrUnknownKey, kid)
	}

	return key, nil
}

// jwkSet is the part of a JWKS document this service reads.
type jwkSet struct {
	Keys []struct {
		Kty string `json:"kty"`
		Kid string `json:"kid"`
		Alg string `json:"alg"`
		Use string `json:"use"`
		N   string `json:"n"`
		E   string `json:"e"`
	} `json:"keys"`
}

func (j *jwks) fetch() (map[string]*rsa.PublicKey, error) {
	response, err := j.client.Get(j.url)
	if err != nil {
		return nil, fmt.Errorf("fetch the key set: %w", err)
	}
	defer func() { _ = response.Body.Close() }()

	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("fetch the key set: %s", response.Status)
	}

	var set jwkSet
	if err = json.NewDecoder(response.Body).Decode(&set); err != nil {
		return nil, fmt.Errorf("decode the key set: %w", err)
	}

	keys := make(map[string]*rsa.PublicKey, len(set.Keys))
	for _, key := range set.Keys {
		// Only the RSA keys meant for signatures are of use here, a realm also
		// publishes its encryption keys.
		if key.Kty != "RSA" || (key.Use != "" && key.Use != "sig") {
			continue
		}

		if key.Alg != "" && key.Alg != signingAlgorithm {
			continue
		}

		parsed, err := parseRSAKey(key.N, key.E)
		if err != nil {
			return nil, fmt.Errorf("read the signing key %q: %w", key.Kid, err)
		}

		keys[key.Kid] = parsed
	}

	if len(keys) == 0 {
		return nil, fmt.Errorf("the key set holds no %s signing key", signingAlgorithm)
	}

	return keys, nil
}

// parseRSAKey rebuilds a public key from the base64url modulus and exponent a
// JWKS reports.
func parseRSAKey(modulus, exponent string) (*rsa.PublicKey, error) {
	n, err := base64.RawURLEncoding.DecodeString(modulus)
	if err != nil {
		return nil, fmt.Errorf("modulus: %w", err)
	}

	e, err := base64.RawURLEncoding.DecodeString(exponent)
	if err != nil {
		return nil, fmt.Errorf("exponent: %w", err)
	}

	if len(n) == 0 || len(e) == 0 {
		return nil, fmt.Errorf("modulus and exponent must both be set")
	}

	return &rsa.PublicKey{
		N: new(big.Int).SetBytes(n),
		E: int(new(big.Int).SetBytes(e).Int64()),
	}, nil
}
