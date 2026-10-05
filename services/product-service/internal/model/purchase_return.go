package model

import "github.com/shopspring/decimal"

type PurchaseReturn struct {
	Id             int64           `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	DocumentNumber string          `gorm:"column:document_number;type:varchar(30);not null" json:"document_number"`
	SupplierId     int64           `gorm:"column:supplier_id;type:bigint" json:"supplier_id"`
	Status         string          `gorm:"column:status;type:varchar(20);not null" json:"status"`
	GrandTotal     decimal.Decimal `gorm:"column:grand_total;type:decimal(10,2);not null" json:"grand_total" validate:"required,gt=0"`
	Reason         string          `gorm:"column:reason;type:text" json:"reason"`
	Note           string          `gorm:"column:note;type:text" json:"note"`
	Base

	// Relations
	Supplier             *Supplier               `gorm:"foreignKey:SupplierId;references:Id" json:"supplier"`
	PurchaseReturnDetail *[]PurchaseReturnDetail `gorm:"foreignKey:PurchaseReturnId;references:Id" json:"purchase_return_detail"`
}

func (PurchaseReturn) TableName() string {
	return "purchase_return"
}
