package category

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Create implement repository for create new category
func Create(orm *gorm.DB, category model.Category) (model.Category, error) {
	err := orm.Create(&category).Error
	if err != nil {
		return model.Category{}, err
	}

	return category, nil
}
