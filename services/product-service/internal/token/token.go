// Package token verifies the access tokens the requests carry and authorizes
// them against the realm roles they claim.
//
// Tokens are issued by Keycloak, so this service only verifies them: the RS256
// signature is checked against the realm's JWKS endpoint, and iss, exp and sub
// are checked while parsing. A wrong issuer therefore rejects every token,
// because it must equal the iss claim exactly.
package token

import (
	"errors"
	"fmt"
	"slices"

	"product-service/internal/constant"
	"product-service/internal/exception"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

// signingAlgorithm is the only algorithm a token may be signed with. Accepting
// anything else would let a caller pick one this service did not intend, so the
// parser is given this single entry rather than trusting the alg header.
const signingAlgorithm = "RS256"

// Claims is what a verified token says about the caller.
type Claims struct {
	// Subject is the account the token was issued for, the value stamped on the
	// rows a request writes.
	Subject uuid.UUID
	// Roles are the realm roles the token carries.
	Roles []string
}

// HasAnyRole reports whether the token carries at least one of the roles an
// endpoint accepts.
func (c Claims) HasAnyRole(roles ...string) bool {
	for _, role := range roles {
		if slices.Contains(c.Roles, role) {
			return true
		}
	}

	return false
}

// Verifier checks an access token and reports what it claims.
type Verifier interface {
	Verify(token string) (Claims, error)
}

type verifier struct {
	issuer string
	keys   KeySource
}

// NewVerifier builds the verifier of a realm. The issuer must be the realm base
// url exactly as the tokens report it.
func NewVerifier(issuer string, keys KeySource) Verifier {
	return verifier{issuer: issuer, keys: keys}
}

// Verify parses the token, checks its signature and claims, and returns what it
// says about the caller. The error is always an exception carrying the status
// the caller should be answered with.
func (v verifier) Verify(raw string) (Claims, error) {
	var keyErr error

	parsed, err := jwt.Parse(raw, func(t *jwt.Token) (any, error) {
		kid, _ := t.Header["kid"].(string)

		key, err := v.keys.Key(kid)
		if err != nil {
			// Kept aside so a key set that could not be read is told apart from a
			// token that is simply signed by nobody this realm knows.
			keyErr = err

			return nil, err
		}

		return key, nil
	},
		jwt.WithValidMethods([]string{signingAlgorithm}),
		jwt.WithIssuer(v.issuer),
		jwt.WithExpirationRequired(),
	)
	if err != nil {
		return Claims{}, verifyError(err, keyErr)
	}

	return readClaims(parsed)
}

// verifyError maps a parse failure onto the status the caller is answered with:
// a token this service could not read is the caller's fault, a key set it could
// not read is its own.
func verifyError(err, keyErr error) error {
	if keyErr != nil && errors.Is(err, jwt.ErrTokenUnverifiable) {
		// A token naming a key the realm does not publish came from somewhere
		// else, so the caller is told to log in again. Only a key set this
		// service could not read at all is its own failure.
		if errors.Is(keyErr, ErrUnknownKey) {
			return exception.ErrTokenRejected.Wrap(err)
		}

		return exception.ErrTokenUnverifiable.Wrap(err)
	}

	if errors.Is(err, jwt.ErrTokenMalformed) {
		return exception.ErrTokenMalformed.Wrap(err)
	}

	return exception.ErrTokenRejected.Wrap(err)
}

// readClaims pulls the subject and the realm roles out of a verified token.
func readClaims(parsed *jwt.Token) (Claims, error) {
	subject, err := parsed.Claims.GetSubject()
	if err != nil || subject == "" {
		return Claims{}, exception.ErrTokenRejected.Wrap(fmt.Errorf("token carries no subject"))
	}

	// The subject identifies the account on every row this service writes, so a
	// subject that is not an id it can store is a malformed token.
	id, err := uuid.Parse(subject)
	if err != nil {
		return Claims{}, exception.ErrTokenMalformed.Wrap(err)
	}

	return Claims{Subject: id, Roles: realmRoles(parsed)}, nil
}

// realmRoles reads realm_access.roles, which is where Keycloak puts the realm
// roles. A token without them simply carries none.
func realmRoles(parsed *jwt.Token) []string {
	claims, ok := parsed.Claims.(jwt.MapClaims)
	if !ok {
		return nil
	}

	access, ok := claims[constant.RealmAccessClaim].(map[string]any)
	if !ok {
		return nil
	}

	list, ok := access[constant.RolesKey].([]any)
	if !ok {
		return nil
	}

	roles := make([]string, 0, len(list))
	for _, role := range list {
		if name, ok := role.(string); ok {
			roles = append(roles, name)
		}
	}

	return roles
}
