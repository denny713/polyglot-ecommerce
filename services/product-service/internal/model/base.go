package model

import (
	"context"
	"time"

	"product-service/internal/account"

	"github.com/google/uuid"
)

type Base struct {
	IsActive  bool      `gorm:"column:is_active" json:"is_active"`
	IsDeleted bool      `gorm:"column:is_deleted" json:"is_deleted"`
	CreatedBy uuid.UUID `gorm:"column:created_by" json:"created_by"`
	UpdatedBy uuid.UUID `gorm:"column:updated_by" json:"updated_by"`
	CreatedAt time.Time `gorm:"column:created_at" json:"created_at"`
	UpdatedAt time.Time `gorm:"column:updated_at" json:"updated_at"`
}

// PrePersist stamps a row that is about to be written for the first time. The
// audit fields name the account the request was made by, which the token
// middleware put on the context as the subject of the verified access token.
func PrePersist(ctx context.Context) Base {
	caller := account.UserLogin(ctx)

	return Base{
		IsActive:  true,
		IsDeleted: false,
		CreatedBy: caller,
		UpdatedBy: caller,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
	}
}

func PreUpdate(ctx context.Context) Base {
	return Base{
		UpdatedBy: account.UserLogin(ctx),
		UpdatedAt: time.Now(),
	}
}

// Touch stamps the audit fields of a record that was loaded from the database
// and is about to be written back. Unlike PreUpdate it keeps the status flags
// and the creation trail already held by the record, so it is the one to use
// when only a flag such as IsActive or IsDeleted changes.
func (b Base) Touch(ctx context.Context) Base {
	b.UpdatedBy = account.UserLogin(ctx)
	b.UpdatedAt = time.Now()

	return b
}
