package model

import (
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
)

func TestTableName(t *testing.T) {
	require.Equal(t, "category", Category{}.TableName())
	require.Equal(t, "product", Product{}.TableName())
	require.Equal(t, "supplier", Supplier{}.TableName())
	require.Equal(t, "stock", Stock{}.TableName())
	require.Equal(t, "stock_position", StockPosition{}.TableName())
	require.Equal(t, "purchase_order", PurchaseOrder{}.TableName())
	require.Equal(t, "purchase_order_detail", PurchaseOrderDetail{}.TableName())
	require.Equal(t, "purchase_return", PurchaseReturn{}.TableName())
	require.Equal(t, "purchase_return_detail", PurchaseReturnDetail{}.TableName())
}

func TestPrePersist(t *testing.T) {
	before := time.Now()
	base := PrePersist()

	require.True(t, base.IsActive)
	require.False(t, base.IsDeleted)
	require.NotEqual(t, uuid.Nil, base.CreatedBy)
	require.NotEqual(t, uuid.Nil, base.UpdatedBy)
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
	require.Equal(t, uuid.Nil, base.CreatedBy)
	require.True(t, base.CreatedAt.IsZero())
	require.NotEqual(t, uuid.Nil, base.UpdatedBy)
	require.False(t, base.UpdatedAt.Before(before))
}

func TestTouchKeepsTheExistingTrail(t *testing.T) {
	created := time.Date(2024, time.March, 2, 10, 0, 0, 0, time.UTC)
	actor := uuid.MustParse("2b1f8f4a-0000-4000-8000-00000000002a")
	base := Base{
		IsActive:  true,
		IsDeleted: true,
		CreatedBy: actor,
		UpdatedBy: actor,
		CreatedAt: created,
		UpdatedAt: created,
	}

	before := time.Now()
	touched := base.Touch()

	require.True(t, touched.IsActive)
	require.True(t, touched.IsDeleted)
	require.Equal(t, actor, touched.CreatedBy)
	require.Equal(t, created, touched.CreatedAt)
	require.NotEqual(t, uuid.Nil, touched.UpdatedBy)
	require.False(t, touched.UpdatedAt.Before(before))

	// Touch works on a copy, the record it was called on is untouched.
	require.Equal(t, created, base.UpdatedAt)
	require.Equal(t, actor, base.UpdatedBy)
}
