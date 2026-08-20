package product

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Search implement repository for search product by name and description
func Search(orm *gorm.DB, name string, description string) ([]model.Product, error) {
	products := make([]model.Product, 0)
	descQuery := "%" + description + "%"
	nameQuery := "%" + name + "%"

	err := orm.Preload("Stock").
		Where("is_deleted = FALSE").
		Where("name ILIKE ? AND description ILIKE ?", nameQuery, descQuery).
		Order("ID DESC").
		Find(&products).Error
	if err != nil {
		return nil, err
	}

	return products, nil
}
