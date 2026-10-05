package stock

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Create implement repository for create new stock position
func (r repository) Create(orm *gorm.DB, position model.StockPosition) (model.StockPosition, error) {
	err := orm.Create(&position).Error
	if err != nil {
		return model.StockPosition{}, err
	}

	return position, nil
}
