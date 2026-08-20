package stock

import (
	"product-service/internal/configuration"
	"product-service/internal/model"
)

// Create implement repository for create new stock
func Create(stock model.Stock) (model.Stock, error) {
	err := configuration.DB.Create(&stock).Error
	if err != nil {
		return model.Stock{}, err
	}

	return stock, nil
}
