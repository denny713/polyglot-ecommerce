package product

import (
	"product-service/internal/configuration"
	"product-service/internal/model"
)

func Update(product model.Product) (model.Product, error) {
	err := configuration.DB.Model(&product).
		Where("is_deleted = FALSE").
		Where("product_id = ?", product.ID).
		Select("Name", "Description", "Price", "IsActive", "IsDeleted", "CreatedAt", "UpdatedAt").
		Updates(product).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
