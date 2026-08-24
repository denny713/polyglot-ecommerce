package product

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Update implement repository for update product
func Update(orm *gorm.DB, product model.Product) (model.Product, error) {
	var err error

	updates := map[string]interface{}{
		"Name":        product.Name,
		"Description": product.Description,
		"Price":       product.Price,
		"ImageURL":    product.ImageURL,
		"IsActive":    product.IsActive,
		"IsDeleted":   product.IsDeleted,
		"UpdatedBy":   product.UpdatedBy,
		"UpdatedAt":   product.UpdatedAt,
	}

	err = orm.Model(&product).
		Where("is_deleted = FALSE").
		Where("id = ?", product.Id).
		Updates(updates).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
