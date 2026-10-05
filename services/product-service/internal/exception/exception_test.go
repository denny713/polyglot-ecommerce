package exception

import (
	"errors"
	"net/http"
	"testing"

	"github.com/labstack/echo/v5"
	"github.com/stretchr/testify/require"
)

func TestConstructors(t *testing.T) {
	tests := []struct {
		name      string
		exception *Exception
		status    int
	}{
		{"new", New(http.StatusTeapot, "teapot"), http.StatusTeapot},
		{"bad request", BadRequest("bad"), http.StatusBadRequest},
		{"unauthorized", Unauthorized("unauthorized"), http.StatusUnauthorized},
		{"forbidden", Forbidden("forbidden"), http.StatusForbidden},
		{"not found", NotFound("missing"), http.StatusNotFound},
		{"conflict", Conflict("conflict"), http.StatusConflict},
		{"internal", Internal("internal"), http.StatusInternalServerError},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			require.Equal(t, test.status, test.exception.Status)
			require.NotEmpty(t, test.exception.Message)
		})
	}
}

func TestErrorMessage(t *testing.T) {
	require.Equal(t, "missing", NotFound("missing").Error())
	require.Equal(t, "missing: no rows", NotFound("missing").Wrap(errors.New("no rows")).Error())
}

func TestWrapKeepsTheSentinelComparable(t *testing.T) {
	wrapped := ErrNotFound.Wrap(errors.New("no rows"))

	require.ErrorIs(t, wrapped, ErrNotFound)
	require.EqualError(t, wrapped.Unwrap(), "no rows")

	// The sentinel itself is not modified by the copy.
	require.Nil(t, ErrNotFound.Unwrap())
}

func TestIs(t *testing.T) {
	require.True(t, ErrNotFound.Is(NotFound("The specific data not found")))
	require.False(t, ErrNotFound.Is(NotFound("something else")))
	require.False(t, ErrNotFound.Is(Conflict("The specific data not found")))
	require.False(t, ErrNotFound.Is(errors.New("plain")))
}

func TestResolve(t *testing.T) {
	require.Nil(t, Resolve(nil))

	require.Same(t, ErrAlreadyActive, Resolve(ErrAlreadyActive))

	// An error that is not an exception is an unexpected failure.
	resolved := Resolve(errors.New("boom"))
	require.Equal(t, http.StatusInternalServerError, resolved.Status)
	require.Equal(t, "boom", resolved.Message)

	// The exception is pulled out of a chain it was wrapped into.
	require.Equal(t, http.StatusNotFound, Resolve(errors.Join(errors.New("outer"), ErrNotFound)).Status)
}

func TestHTTPError(t *testing.T) {
	require.NoError(t, HTTPError(nil))

	var httpError *echo.HTTPError
	require.ErrorAs(t, HTTPError(ErrInvalidIdentifier), &httpError)
	require.Equal(t, http.StatusBadRequest, httpError.Code)
	require.Equal(t, ErrInvalidIdentifier.Message, httpError.Message)

	require.ErrorAs(t, HTTPError(errors.New("boom")), &httpError)
	require.Equal(t, http.StatusInternalServerError, httpError.Code)
	require.Equal(t, "boom", httpError.Message)
}

func TestSentinels(t *testing.T) {
	require.Equal(t, http.StatusBadRequest, ErrInvalidIdentifier.Status)
	require.Equal(t, http.StatusNotFound, ErrNotFound.Status)
	require.Equal(t, http.StatusConflict, ErrAlreadyActive.Status)
	require.Equal(t, http.StatusConflict, ErrAlreadyInactive.Status)
}
