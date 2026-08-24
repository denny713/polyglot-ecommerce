package category

import (
	"product-service/internal/dto/category"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Search implement repository for search category by name and description
func Search(orm *gorm.DB, filter category.CategorySearchFilter) ([]model.Category, error) {
	categories := make([]model.Category, 0)

	query := orm.Model(&model.Category{}).
		Select("category.*").
		Where("category.is_deleted = FALSE")

	if filter.Name != "" {
		query = query.Where("category.name ILIKE ?", "%"+filter.Name+"%")
	}

	if filter.Description != "" {
		query = query.Where("category.description ILIKE ?", "%"+filter.Description+"%")
	}

	if filter.Limit > 0 {
		query = query.Limit(filter.Limit)
	}

	if filter.Offset > 0 {
		query = query.Offset(filter.Offset)
	}

	err := query.Order(category.OrderClause(filter)).Find(&categories).Error
	if err != nil {
		return nil, err
	}

	return categories, nil
}
