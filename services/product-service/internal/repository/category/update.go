package category

import (
	"product-service/internal/model"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// Update implement repository for update category
func (r repository) Update(orm *gorm.DB, category model.Category) (model.Category, error) {
	var err error

	updates := map[string]interface{}{
		"Name":        category.Name,
		"Description": category.Description,
		"IsActive":    category.IsActive,
		"IsDeleted":   category.IsDeleted,
		"UpdatedBy":   category.UpdatedBy,
		"UpdatedAt":   category.UpdatedAt,
	}

	// The model carries the relations that Detail preloaded. They are omitted
	// so gorm writes only the columns in the map, a preloaded relation would
	// otherwise be saved alongside and assign its foreign key a second time.
	err = orm.Model(&category).
		Omit(clause.Associations).
		Where("is_deleted = FALSE").
		Where("id = ?", category.Id).
		Updates(updates).Error
	if err != nil {
		return category, err
	}

	return category, nil
}
