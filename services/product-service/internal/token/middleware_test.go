package token

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"product-service/internal/account"
	"product-service/internal/constant"
	"product-service/internal/exception"

	"github.com/google/uuid"
	"github.com/labstack/echo/v5"
	"github.com/stretchr/testify/require"
)

// stubVerifier answers with what a test scripted, so the middleware is exercised
// without minting a token.
type stubVerifier struct {
	claims Claims
	err    error

	calls []string
}

func (s *stubVerifier) Verify(raw string) (Claims, error) {
	s.calls = append(s.calls, raw)

	return s.claims, s.err
}

var caller = uuid.MustParse("11111111-2222-3333-4444-555555555555")

// guarded builds a context through the middleware and reports what the handler
// behind it saw.
func guarded(t *testing.T, header string, verifier Verifier, roles ...string) (error, *uuid.UUID) {
	t.Helper()

	request := httptest.NewRequest(http.MethodGet, "/api/product/7", nil)
	if header != "" {
		request.Header.Set(constant.AuthorizationHeader, header)
	}

	c := echo.New().NewContext(request, httptest.NewRecorder())

	var reached *uuid.UUID
	handler := func(c *echo.Context) error {
		seen := account.UserLogin(c.Request().Context())
		reached = &seen

		return c.NoContent(http.StatusOK)
	}

	return Authorize(verifier, roles...)(handler)(c), reached
}

// httpError asserts err is an echo error carrying the given status and message.
func httpError(t *testing.T, err error, status int, message string) {
	t.Helper()

	var echoError *echo.HTTPError
	require.ErrorAs(t, err, &echoError)
	require.Equal(t, status, echoError.Code)
	require.Equal(t, message, echoError.Message)
}

func TestAuthorize(t *testing.T) {
	verifier := &stubVerifier{claims: Claims{Subject: caller, Roles: []string{constant.AdminRole}}}

	err, reached := guarded(t, "Bearer the-token", verifier, constant.AdminRole)

	require.NoError(t, err)
	require.Equal(t, []string{"the-token"}, verifier.calls)

	// The handler runs, and the caller the token reported travels with it so a
	// write below can stamp who made it.
	require.NotNil(t, reached, "the handler was not reached")
	require.Equal(t, caller, *reached)
}

func TestAuthorizeAcceptsAnyOfTheRoles(t *testing.T) {
	// The product detail names two roles, a plain user is enough for it.
	verifier := &stubVerifier{claims: Claims{Subject: caller, Roles: []string{constant.UserRole}}}

	err, reached := guarded(t, "Bearer the-token", verifier, constant.AdminRole, constant.UserRole)

	require.NoError(t, err)
	require.NotNil(t, reached)
}

func TestAuthorizeWithoutAToken(t *testing.T) {
	for name, header := range map[string]string{
		"no header":       "",
		"another scheme":  "Basic dXNlcjpwYXNz",
		"prefix only":     "Bearer ",
		"prefix at space": "Bearer    ",
		"unprefixed":      "the-token",
		"lowercase":       "bearer the-token",
	} {
		t.Run(name, func(t *testing.T) {
			verifier := &stubVerifier{}

			err, reached := guarded(t, header, verifier, constant.AdminRole)

			httpError(t, err, http.StatusUnauthorized, exception.ErrTokenMissing.Message)
			require.Nil(t, reached, "the handler must not run")
			require.Empty(t, verifier.calls, "an unusable header is refused before verifying")
		})
	}
}

func TestAuthorizeWhenTheTokenIsRefused(t *testing.T) {
	verifier := &stubVerifier{err: exception.ErrTokenRejected}

	err, reached := guarded(t, "Bearer the-token", verifier, constant.AdminRole)

	// The status the verifier chose is the one the caller is answered with.
	httpError(t, err, http.StatusUnauthorized, exception.ErrTokenRejected.Message)
	require.Nil(t, reached)
}

func TestAuthorizeWhenTheKeysCannotBeRead(t *testing.T) {
	verifier := &stubVerifier{err: exception.ErrTokenUnverifiable.Wrap(errors.New("dial tcp: refused"))}

	err, _ := guarded(t, "Bearer the-token", verifier, constant.AdminRole)

	// A key set this service could not read is its own failure, not the caller's.
	httpError(t, err, http.StatusInternalServerError, exception.ErrTokenUnverifiable.Message)
}

func TestAuthorizeWithoutTheRole(t *testing.T) {
	verifier := &stubVerifier{claims: Claims{Subject: caller, Roles: []string{constant.UserRole}}}

	err, reached := guarded(t, "Bearer the-token", verifier, constant.AdminRole)

	// A verified user without the admin role is refused, not challenged again.
	httpError(t, err, http.StatusForbidden, exception.ErrForbidden.Message)
	require.Nil(t, reached, "the handler must not run")
}

func TestAuthorizeWithNoRolesAtAll(t *testing.T) {
	verifier := &stubVerifier{claims: Claims{Subject: caller}}

	err, _ := guarded(t, "Bearer the-token", verifier, constant.AdminRole)

	httpError(t, err, http.StatusForbidden, exception.ErrForbidden.Message)
}

func TestHasAnyRole(t *testing.T) {
	claims := Claims{Roles: []string{"offline_access", constant.UserRole}}

	require.True(t, claims.HasAnyRole(constant.UserRole))
	require.True(t, claims.HasAnyRole(constant.AdminRole, constant.UserRole))
	require.False(t, claims.HasAnyRole(constant.AdminRole))
	require.False(t, claims.HasAnyRole(), "a route naming no role accepts nobody")
	require.False(t, Claims{}.HasAnyRole(constant.AdminRole))
}
