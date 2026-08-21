package model

type Stock struct {
	Id             int64  `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	ProductId      int64  `gorm:"column:product_id;type:bigint" json:"product_id"`
	DocumentNumber string `gorm:"column:document_number;type:varchar(30)" json:"document_number"`
	DocumentType   string `gorm:"column:document_type;type:varchar(10)" json:"document_type"`
	Activity       string `gorm:"column:activity;type:varchar(5)" json:"activity"`
	Quantity       int    `gorm:"column:quantity;type:int" json:"quantity"`
	Base

	// Relation
	Product *Product `gorm:"foreignKey:ProductId;references:Id" json:"product"`
}

func (Stock) TableName() string {
	return "stock"
}
