package product

import (
	"product-service/internal/configuration"
	"product-service/internal/model"
)

// Create implement repository for create new product
func Create(product model.Product) (model.Product, error) {
	err := configuration.DB.Create(&product).Error
	if err != nil {
		return model.Product{}, err
	}

	return product, nil
}
