package product

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Update implement repository for update product
func Update(orm *gorm.DB, product model.Product) (model.Product, error) {
	err := orm.Model(&product).
		Where("is_deleted = FALSE").
		Where("id = ?", product.ID).
		Select("Name", "Description", "Price", "IsActive", "IsDeleted", "UpdatedAt").
		Updates(product).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
