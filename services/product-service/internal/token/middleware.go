package token

import (
	"strings"

	"product-service/internal/account"
	"product-service/internal/constant"
	"product-service/internal/exception"

	"github.com/labstack/echo/v5"
)

// Authorize builds the middleware guarding a route. A request must carry a
// token this service can verify, and that token must claim at least one of the
// given roles.
//
// The roles are named per route rather than checked once for the whole service,
// because the product detail is readable by a plain user while everything else
// is administrative.
func Authorize(verifier Verifier, roles ...string) echo.MiddlewareFunc {
	return func(next echo.HandlerFunc) echo.HandlerFunc {
		return func(c *echo.Context) error {
			raw := bearerToken(c)
			if raw == "" {
				return exception.HTTPError(exception.ErrTokenMissing)
			}

			claims, err := verifier.Verify(raw)
			if err != nil {
				return exception.HTTPError(err)
			}

			if !claims.HasAnyRole(roles...) {
				return exception.HTTPError(exception.ErrForbidden)
			}

			// The subject travels on the request context, so a handler and the
			// services below it can stamp the account a row was written by without
			// being handed the id call by call.
			request := c.Request()
			c.SetRequest(request.WithContext(account.WithUserLogin(request.Context(), claims.Subject)))

			return next(c)
		}
	}
}

// bearerToken reads the access token off the request. Anything unusable answers
// empty, so the caller has one case to handle instead of also guarding against
// a bearer prefix with nothing behind it.
func bearerToken(c *echo.Context) string {
	header := c.Request().Header.Get(constant.AuthorizationHeader)
	if !strings.HasPrefix(header, constant.BearerPrefix) {
		return ""
	}

	return strings.TrimSpace(strings.TrimPrefix(header, constant.BearerPrefix))
}
