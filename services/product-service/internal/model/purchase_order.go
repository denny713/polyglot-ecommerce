package model

import "github.com/shopspring/decimal"

type PurchaseOrder struct {
	Id              int64           `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	DocumentNumber  string          `gorm:"column:document_number;type:varchar(30);not null" json:"document_number"`
	SupplierId      int64           `gorm:"column:supplier_id;type:bigint" json:"supplier_id"`
	Status          string          `gorm:"column:status;type:varchar(20);not null" json:"status"`
	OrderGrandTotal decimal.Decimal `gorm:"column:order_grand_total;type:decimal(10,2);not null" json:"order_grand_total" validate:"required,gt=0"`
	RealGrandTotal  decimal.Decimal `gorm:"column:real_grand_total;type:decimal(10,2);not null" json:"real_grand_total" validate:"required,gt=0"`
	Note            string          `gorm:"column:note;type:text" json:"note"`
	Base

	// Relations
	Supplier            *Supplier              `gorm:"foreignKey:SupplierId;references:Id" json:"supplier"`
	PurchaseOrderDetail *[]PurchaseOrderDetail `gorm:"foreignKey:PurchaseOrderId;references:Id" json:"purchase_order_detail"`
}

func (PurchaseOrder) TableName() string {
	return "purchase_order"
}
