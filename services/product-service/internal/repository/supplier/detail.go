package supplier

import (
	"fmt"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Detail implement repository for get supplier detail
func Detail(orm *gorm.DB, param string, value interface{}) (model.Supplier, error) {
	var supplier model.Supplier
	err := orm.Preload("Products", "is_deleted = FALSE").Where(
		fmt.Sprintf("%s = ? AND is_deleted = FALSE", param), value).
		First(&supplier).Error
	if err != nil {
		return supplier, err
	}

	return supplier, nil
}
