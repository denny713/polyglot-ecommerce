package category

import (
	"product-service/internal/dto/base"
	"product-service/internal/dto/category"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// sortColumns maps user-facing sort keys to database column names for category.
var sortColumns = map[string]string{
	"id":          "category.id",
	"name":        "category.name",
	"description": "category.description",
	"created_at":  "category.created_at",
	"updated_at":  "category.updated_at",
}

// Search implement repository for search category by name and description
func (r repository) Search(orm *gorm.DB, filter category.CategorySearchFilter) ([]model.Category, error) {
	categories := make([]model.Category, 0)

	query := orm.Preload("Products", "is_deleted = FALSE").
		Model(&model.Category{}).
		Select("category.*").
		Where("category.is_deleted = FALSE")

	if filter.Name != "" {
		query = query.Where("category.name ILIKE ?", "%"+filter.Name+"%")
	}

	if filter.Description != "" {
		query = query.Where("category.description ILIKE ?", "%"+filter.Description+"%")
	}

	if limit := filter.Paging.Limit(); limit > 0 {
		query = query.Limit(limit)
	}

	if offset := filter.Paging.Offset(); offset > 0 {
		query = query.Offset(offset)
	}

	err := query.Order(base.OrderClause(sortColumns, filter.SortBy, filter.SortOrder)).Find(&categories).Error
	if err != nil {
		return nil, err
	}

	return categories, nil
}
