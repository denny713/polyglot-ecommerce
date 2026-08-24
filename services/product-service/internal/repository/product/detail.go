package product

import (
	"fmt"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Detail implement repository for get product detail
func Detail(orm *gorm.DB, param string, value interface{}) (model.Product, error) {
	var product model.Product
	// The Stock relation is commented out on model.Product, so it cannot be
	// preloaded here yet. Restore the Preload together with the relation.
	err := orm.Where(
		fmt.Sprintf("%s = ? AND is_deleted = FALSE", param), value).
		First(&product).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
