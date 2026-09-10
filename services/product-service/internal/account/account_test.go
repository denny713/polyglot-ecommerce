package account

import (
	"context"
	"testing"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
)

func TestUserLogin(t *testing.T) {
	caller := uuid.MustParse("11111111-2222-3333-4444-555555555555")

	ctx := WithUserLogin(context.Background(), caller)

	require.Equal(t, caller, UserLogin(ctx))
}

func TestUserLoginWithoutOne(t *testing.T) {
	// A write made outside a request, from a task or a test, audits nothing
	// rather than failing on a context that never carried a caller.
	require.Equal(t, uuid.Nil, UserLogin(context.Background()))
}

func TestUserLoginIgnoresAValueOfAnotherType(t *testing.T) {
	// The key is unexported, so nothing else can land under it. Reading a
	// mistyped value still answers uuid.Nil instead of panicking.
	ctx := context.WithValue(context.Background(), userLoginKey{}, "not-a-uuid")

	require.Equal(t, uuid.Nil, UserLogin(ctx))
}

func TestUserLoginIsPerContext(t *testing.T) {
	first := uuid.MustParse("11111111-1111-1111-1111-111111111111")
	second := uuid.MustParse("22222222-2222-2222-2222-222222222222")

	base := context.Background()
	one := WithUserLogin(base, first)
	two := WithUserLogin(base, second)

	// One request cannot read the caller of another.
	require.Equal(t, first, UserLogin(one))
	require.Equal(t, second, UserLogin(two))
	require.Equal(t, uuid.Nil, UserLogin(base))
}
