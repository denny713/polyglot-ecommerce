package model

type StockPosition struct {
	Id        int64 `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	ProductId int64 `gorm:"column:product_id;type:bigint" json:"product_id"`
	Quantity  int   `gorm:"column:quantity;type:int" json:"quantity"`
	Base

	// Relation
	//Product *Product `gorm:"foreignKey:ProductId;references:Id" json:"product"`
}

func (StockPosition) TableName() string {
	return "stock_position"
}
