package product

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Create implement repository for create new product
func Create(orm *gorm.DB, product model.Product) (model.Product, error) {
	err := orm.Create(&product).Error
	if err != nil {
		return model.Product{}, err
	}

	return product, nil
}
