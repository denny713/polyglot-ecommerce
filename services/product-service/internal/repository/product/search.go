package product

import (
	"product-service/internal/configuration"
	"product-service/internal/model"
)

func Search(name string, description string) ([]model.Product, error) {
	products := make([]model.Product, 0)
	descQuery := "%" + description + "%"
	nameQuery := "%" + name + "%"

	err := configuration.DB.Preload("Stock").
		Where("is_deleted = FALSE").
		Where("name ILIKE ? AND description ILIKE ?", nameQuery, descQuery).
		Order("ID DESC").
		Find(&products).Error
	if err != nil {
		return nil, err
	}

	return products, nil
}
