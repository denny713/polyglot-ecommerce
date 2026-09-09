package model

import "github.com/shopspring/decimal"

type PurchaseReturnDetail struct {
	Id               int64           `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	PurchaseReturnId int64           `gorm:"column:purchase_return_id;type:bigint" json:"purchase_return_id"`
	ProductId        int64           `gorm:"column:product_id;type:bigint" json:"product_id"`
	Quantity         int             `gorm:"column:quantity;type:integer;not null" json:"quantity" validate:"required,gt=0"`
	UnitPrice        decimal.Decimal `gorm:"column:unit_price;type:decimal(10,2);not null" json:"unit_price" validate:"required,gt=0"`
	Subtotal         decimal.Decimal `gorm:"column:subtotal;type:decimal(10,2);not null" json:"subtotal" validate:"required,gt=0"`
	Reason           string          `gorm:"column:reason;type:text" json:"reason"`
	Note             string          `gorm:"column:note;type:text" json:"note"`
	Base

	// Relations
	PurchaseReturn *PurchaseReturn `gorm:"foreignKey:PurchaseReturnId;references:Id" json:"purchase_return"`
	Product        *Product        `gorm:"foreignKey:ProductId;references:Id" json:"product"`
}

func (PurchaseReturnDetail) TableName() string {
	return "purchase_return_detail"
}
