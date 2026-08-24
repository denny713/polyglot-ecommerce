package model

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestTableName(t *testing.T) {
	require.Equal(t, "category", Category{}.TableName())
	require.Equal(t, "product", Product{}.TableName())
	require.Equal(t, "supplier", Supplier{}.TableName())
	require.Equal(t, "stock", Stock{}.TableName())
	require.Equal(t, "stock_position", StockPosition{}.TableName())
}

func TestPrePersist(t *testing.T) {
	before := time.Now()
	base := PrePersist()

	require.True(t, base.IsActive)
	require.False(t, base.IsDeleted)
	require.Equal(t, int64(1), base.CreatedBy)
	require.Equal(t, int64(1), base.UpdatedBy)
	require.False(t, base.CreatedAt.Before(before))
	require.False(t, base.UpdatedAt.Before(before))
}

func TestPreUpdate(t *testing.T) {
	before := time.Now()
	base := PreUpdate()

	// PreUpdate stamps only the audit trail, the status flags and the creation
	// trail are left at their zero value on purpose.
	require.False(t, base.IsActive)
	require.False(t, base.IsDeleted)
	require.Zero(t, base.CreatedBy)
	require.True(t, base.CreatedAt.IsZero())
	require.Equal(t, int64(1), base.UpdatedBy)
	require.False(t, base.UpdatedAt.Before(before))
}

func TestTouchKeepsTheExistingTrail(t *testing.T) {
	created := time.Date(2024, time.March, 2, 10, 0, 0, 0, time.UTC)
	base := Base{
		IsActive:  true,
		IsDeleted: true,
		CreatedBy: 42,
		UpdatedBy: 42,
		CreatedAt: created,
		UpdatedAt: created,
	}

	before := time.Now()
	touched := base.Touch()

	require.True(t, touched.IsActive)
	require.True(t, touched.IsDeleted)
	require.Equal(t, int64(42), touched.CreatedBy)
	require.Equal(t, created, touched.CreatedAt)
	require.Equal(t, int64(1), touched.UpdatedBy)
	require.False(t, touched.UpdatedAt.Before(before))

	// Touch works on a copy, the record it was called on is untouched.
	require.Equal(t, created, base.UpdatedAt)
	require.Equal(t, int64(42), base.UpdatedBy)
}
