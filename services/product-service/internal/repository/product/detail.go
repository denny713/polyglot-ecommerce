package product

import (
	"fmt"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Detail implement repository for get product detail
func Detail(orm *gorm.DB, param string, value interface{}) (model.Product, error) {
	var product model.Product
	err := orm.
		Preload("Category", "is_deleted = FALSE").
		Preload("Supplier", "is_deleted = FALSE").
		Preload("Stock", "is_deleted = FALSE").
		Preload("StockPosition", "is_deleted = FALSE").
		Where(fmt.Sprintf("%s = ? AND is_deleted = FALSE", param), value).
		First(&product).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
