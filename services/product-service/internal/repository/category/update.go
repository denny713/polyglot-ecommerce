package category

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Update implement repository for update category
func Update(orm *gorm.DB, category model.Category) (model.Category, error) {
	var err error

	updates := map[string]interface{}{
		"Name":        category.Name,
		"Description": category.Description,
		"IsActive":    category.IsActive,
		"IsDeleted":   category.IsDeleted,
		"UpdatedBy":   category.UpdatedBy,
		"UpdatedAt":   category.UpdatedAt,
	}

	err = orm.Model(&category).
		Where("is_deleted = FALSE").
		Where("id = ?", category.Id).
		Updates(updates).Error
	if err != nil {
		return category, err
	}

	return category, nil
}
