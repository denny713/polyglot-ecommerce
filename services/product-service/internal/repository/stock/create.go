package stock

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Create implement repository for create new stock
func Create(orm *gorm.DB, stock model.Stock) (model.Stock, error) {
	err := orm.Create(&stock).Error
	if err != nil {
		return model.Stock{}, err
	}

	return stock, nil
}
