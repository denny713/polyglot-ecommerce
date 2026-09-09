// Package account carries the identity of the caller a request is served for.
//
// It exists so a write can stamp created_by and updated_by without every
// service being handed the id call by call, the same job AccountUtil does in the
// inventory service. The value lives on the request context rather than in a
// package variable, so one request cannot read the caller of another.
//
// It is a package of its own because the token middleware sets the value and
// the services read it, and both sit below the utility helpers.
package account

import (
	"context"

	"github.com/google/uuid"
)

// userLoginKey is unexported so nothing outside this package can put a value
// under it, which keeps the id on a request the one the verified token reported.
type userLoginKey struct{}

// WithUserLogin returns a context carrying the account a request was made by.
func WithUserLogin(ctx context.Context, userLogin uuid.UUID) context.Context {
	return context.WithValue(ctx, userLoginKey{}, userLogin)
}

// UserLogin reads the account a request was made by. It answers uuid.Nil off a
// context that never went through the authorization middleware, so a write made
// outside a request audits nothing rather than failing.
func UserLogin(ctx context.Context) uuid.UUID {
	userLogin, ok := ctx.Value(userLoginKey{}).(uuid.UUID)
	if !ok {
		return uuid.Nil
	}

	return userLogin
}
