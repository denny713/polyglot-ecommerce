package category

import (
	"fmt"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Detail implement repository for get category detail
func Detail(orm *gorm.DB, param string, value interface{}) (model.Category, error) {
	var category model.Category
	err := orm.Preload("Products", "is_deleted = FALSE").Where(
		fmt.Sprintf("%s = ? AND is_deleted = FALSE", param), value).
		First(&category).Error
	if err != nil {
		return category, err
	}

	return category, nil
}
