// Package exception centralizes the business errors the service returns. An
// exception carries the HTTP status and the message the client should see, so
// a failure is declared once here instead of being re-mapped in every layer.
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

// Shorthands for the statuses the handlers actually answer with.
func BadRequest(message string) *Exception {
	return New(http.StatusBadRequest, message)
}

func Unauthorized(message string) *Exception {
	return New(http.StatusUnauthorized, message)
}

func Forbidden(message string) *Exception {
	return New(http.StatusForbidden, message)
}

func NotFound(message string) *Exception {
	return New(http.StatusNotFound, message)
}

func Conflict(message string) *Exception {
	return New(http.StatusConflict, message)
}

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
func (e *Exception) Unwrap() error { return e.cause }

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

// Product exceptions.
var (
	ErrProductIDInvalid       = BadRequest("product ID must be a valid number")
	ErrProductNotFound        = NotFound("product not found")
	ErrProductAlreadyActive   = Conflict("product already active")
	ErrProductAlreadyInactive = Conflict("product already inactive")
)
