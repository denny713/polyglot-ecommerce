package model

import "github.com/shopspring/decimal"

type PurchaseOrderDetail struct {
	Id              int64           `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	PurchaseOrderId int64           `gorm:"column:purchase_order_id;type:bigint" json:"purchase_order_id"`
	ProductId       int64           `gorm:"column:product_id;type:bigint" json:"product_id"`
	OrderQuantity   int             `gorm:"column:order_quantity;type:integer;not null" json:"order_quantity" validate:"required,gt=0"`
	RealQuantity    int             `gorm:"column:real_quantity;type:integer;not null" json:"real_quantity" validate:"required,gt=0"`
	UnitPrice       decimal.Decimal `gorm:"column:unit_price;type:decimal(10,2);not null" json:"unit_price" validate:"required,gt=0"`
	OrderSubtotal   decimal.Decimal `gorm:"column:order_subtotal;type:decimal(10,2);not null" json:"order_subtotal" validate:"required,gt=0"`
	RealSubtotal    decimal.Decimal `gorm:"column:real_subtotal;type:decimal(10,2);not null" json:"real_subtotal" validate:"required,gt=0"`
	Note            string          `gorm:"column:note;type:text" json:"note"`
	Base

	// Relations
	PurchaseOrder *PurchaseOrder `gorm:"foreignKey:PurchaseOrderId;references:Id" json:"purchase_order"`
	Product       *Product       `gorm:"foreignKey:ProductId;references:Id" json:"product"`
}

func (PurchaseOrderDetail) TableName() string {
	return "purchase_order_detail"
}
