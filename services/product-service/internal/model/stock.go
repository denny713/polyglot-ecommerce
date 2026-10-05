package model

type Stock struct {
	Id               int64  `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	ProductId        int64  `gorm:"column:product_id;type:bigint" json:"product_id"`
	DocumentNumber   string `gorm:"column:document_number;type:varchar(30)" json:"document_number"`
	DocumentType     string `gorm:"column:document_type;type:varchar(10)" json:"document_type"`
	Activity         string `gorm:"column:activity;type:varchar(5)" json:"activity"`
	Quantity         int    `gorm:"column:quantity;type:int" json:"quantity"`
	PurchaseOrderId  int64  `gorm:"column:purchase_order_id;type:bigint" json:"purchase_order_id"`
	PurchaseReturnId int64  `gorm:"column:purchase_return_id;type:bigint" json:"purchase_return_id"`
	Base

	// Relation
	Product        *Product        `gorm:"foreignKey:ProductId;references:Id" json:"product"`
	PurchaseOrder  *PurchaseOrder  `gorm:"foreignKey:PurchaseOrderId;references:Id" json:"purchase_order"`
	PurchaseReturn *PurchaseReturn `gorm:"foreignKey:PurchaseReturnId;references:Id" json:"purchase_return"`
}

func (Stock) TableName() string {
	return "stock"
}
