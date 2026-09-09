package configuration

import (
	"os"
	"strings"

	"product-service/internal/token"
)

// NewTokenVerifier builds the access token verifier from the environment. The
// keys are read from the realm on first use rather than at start up, so the
// service still boots when the identity provider is not up yet.
func NewTokenVerifier() token.Verifier {
	issuer := IssuerURI()

	return token.NewVerifier(issuer, token.NewKeySource(issuer, nil))
}

// IssuerURI reports the realm the tokens must have been issued by. The trailing
// slash is dropped because the value has to equal the iss claim exactly.
func IssuerURI() string {
	issuer := strings.TrimSpace(os.Getenv("KEYCLOAK_ISSUER_URI"))
	if issuer == "" {
		issuer = "http://localhost:8080/realms/ecommerce"
	}

	return strings.TrimSuffix(issuer, "/")
}
