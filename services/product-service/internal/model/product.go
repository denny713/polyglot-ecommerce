package model

import (
	"github.com/shopspring/decimal"
)

type Product struct {
	Id          int64           `gorm:"column:id;primaryKey;autoIncrement" json:"id"`
	Name        string          `gorm:"column:name;type:varchar(100);not null" json:"name" validate:"required,min=3,max=100"`
	Description string          `gorm:"column:description;type:text" json:"description"`
	Price       decimal.Decimal `gorm:"column:price;type:decimal(10,2);not null" json:"price" validate:"required,gt=0"`
	ImageURL    string          `gorm:"column:image_url;type:text" json:"image_url,omitempty"`
	CategoryId  int64           `gorm:"column:category_id;type:bigint" json:"category_id"`
	SupplierId  int64           `gorm:"column:supplier_id;type:bigint" json:"supplier_id"`
	Base

	// Relations
	Stock         *[]Stock       `gorm:"foreignKey:ProductId;references:Id" json:"stock"`
	StockPosition *StockPosition `gorm:"foreignKey:ProductId;references:Id" json:"stock_position"`
	Category      *Category      `gorm:"foreignKey:CategoryId,references:Id" json:"category"`
	Supplier      *Supplier      `gorm:"foreignKey:SupplierId,references:Id" json:"supplier"`
}

func (Product) TableName() string {
	return "product"
}
