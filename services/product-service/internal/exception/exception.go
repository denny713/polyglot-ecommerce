package exception

import (
	"errors"
	"net/http"

	"github.com/labstack/echo/v5"
)

// Exception is an error enriched with the HTTP status it should be reported
// with. Compare it with errors.Is, the sentinels below are the catalog.
type Exception struct {
	Status  int
	Message string
	cause   error
}

// New builds an exception for the given status and message.
func New(status int, message string) *Exception {
	return &Exception{Status: status, Message: message}
}

// BadRequest builds an exception for the 400 Bad Request status.
func BadRequest(message string) *Exception {
	return New(http.StatusBadRequest, message)
}

// Unauthorized builds an exception for the 401 Unauthorized status.
func Unauthorized(message string) *Exception {
	return New(http.StatusUnauthorized, message)
}

// Forbidden builds an exception for the 403 Forbidden status.
func Forbidden(message string) *Exception {
	return New(http.StatusForbidden, message)
}

// NotFound builds an exception for the 404 Not Found status.
func NotFound(message string) *Exception {
	return New(http.StatusNotFound, message)
}

// Conflict builds an exception for the 409 Conflict status.
func Conflict(message string) *Exception {
	return New(http.StatusConflict, message)
}

// Internal builds an exception for the 500 Internal Server Error status.
func Internal(message string) *Exception {
	return New(http.StatusInternalServerError, message)
}

// Error implements the error interface.
func (e *Exception) Error() string {
	if e.cause != nil {
		return e.Message + ": " + e.cause.Error()
	}

	return e.Message
}

// Wrap returns a copy of the exception keeping err as its cause, the message
// stays the one meant for the client while the technical error is preserved.
func (e *Exception) Wrap(err error) *Exception {
	clone := *e
	clone.cause = err

	return &clone
}

// Unwrap exposes the cause so errors.Is and errors.As keep walking the chain.
func (e *Exception) Unwrap() error {
	return e.cause
}

// Is matches a wrapped copy against the sentinel it was built from, the copy is
// a different pointer so the default identity comparison would miss it.
func (e *Exception) Is(target error) bool {
	other, ok := target.(*Exception)

	return ok && other.Status == e.Status && other.Message == e.Message
}

// Resolve pulls the exception out of the error chain. Anything that is not an
// exception is an unexpected failure, so it is reported as an internal error.
func Resolve(err error) *Exception {
	if err == nil {
		return nil
	}

	var exception *Exception
	if errors.As(err, &exception) {
		return exception
	}

	return Internal(err.Error())
}

// HTTPError converts err into the response the controller should return.
func HTTPError(err error) error {
	if err == nil {
		return nil
	}

	exception := Resolve(err)

	return echo.NewHTTPError(exception.Status, exception.Message)
}

// Security exceptions. The messages match the ones the inventory service
// reports, so a client sees the same wording whichever service refused it.
var (
	ErrTokenMissing      = Unauthorized("No access token found, please login first")
	ErrTokenMalformed    = BadRequest("Invalid token format")
	ErrTokenRejected     = Unauthorized("Access token is invalid or expired, please login again")
	ErrTokenUnverifiable = Internal("Error occurred while processing token")
	ErrForbidden         = Forbidden("You don't have permission to access this resource")
)

// Data exceptions.
var (
	ErrInvalidIdentifier = BadRequest("Identifier must be a valid number")
	ErrNotFound          = NotFound("The specific data not found")
	ErrAlreadyActive     = Conflict("The specific data already active")
	ErrAlreadyInactive   = Conflict("The specific data already inactive")
)
