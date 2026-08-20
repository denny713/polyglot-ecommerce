package product

import (
	"product-service/internal/model"
	"time"

	"gorm.io/gorm"
)

// Update implement repository for update product
func Update(orm *gorm.DB, product model.Product) (model.Product, error) {
	var err error

	updates := map[string]interface{}{
		"Name":        product.Name,
		"Description": product.Description,
		"Price":       product.Price,
		"IsActive":    product.IsActive,
		"IsDeleted":   product.IsDeleted,
		"UpdatedAt":   time.Now(),
	}

	err = orm.Model(&product).
		Where("is_deleted = FALSE").
		Where("id = ?", product.ID).
		Updates(updates).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
