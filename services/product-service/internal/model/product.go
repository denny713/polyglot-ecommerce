package model

import (
	"time"

	"github.com/shopspring/decimal"
)

type Product struct {
	ID          int64           `json:"id"`
	Name        string          `json:"name" validate:"required,min=3,max=100"`
	Description string          `json:"description"`
	Price       decimal.Decimal `json:"price" validate:"required,gt=0"`
	ImageURL    string          `json:"image_url,omitempty"`
	IsActive    bool            `json:"is_active"`
	IsDeleted   bool            `json:"is_deleted"`
	CreatedAt   time.Time       `json:"created_at"`
	UpdatedAt   time.Time       `json:"updated_at"`

	// Stock is a has one relation, the foreign key lives on the stock table.
	Stock *Stock `gorm:"foreignKey:ProductID;references:ID" json:"stock"`
}

func (Product) TableName() string {
	return "product"
}
