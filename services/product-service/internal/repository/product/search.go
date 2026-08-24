package product

import (
	"product-service/internal/dto/product"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Search implement repository for search product by name, description, price range and stock range
func Search(orm *gorm.DB, filter product.ProductSearchFilter) ([]model.Product, error) {
	products := make([]model.Product, 0)

	query := orm.Model(&model.Product{}).
		Select("product.*").
		Preload("StockPosition").
		Where("product.is_deleted = FALSE")

	if filter.Name != "" {
		query = query.Where("product.name ILIKE ?", "%"+filter.Name+"%")
	}

	if filter.Description != "" {
		query = query.Where("product.description ILIKE ?", "%"+filter.Description+"%")
	}

	if filter.MinPrice.IsPositive() {
		query = query.Where("product.price >= ?", filter.MinPrice)
	}

	if filter.MaxPrice.IsPositive() {
		query = query.Where("product.price <= ?", filter.MaxPrice)
	}

	if filter.MinStock > 0 || filter.MaxStock > 0 || filter.SortBy == "stock" {
		query = query.Joins("JOIN stock_position ON stock_position.product_id = product.id")
	}

	if filter.MinStock > 0 {
		query = query.Where("stock_position.quantity >= ?", filter.MinStock)
	}

	if filter.MaxStock > 0 {
		query = query.Where("stock_position.quantity <= ?", filter.MaxStock)
	}

	if filter.Limit > 0 {
		query = query.Limit(filter.Limit)
	}

	if filter.Offset > 0 {
		query = query.Offset(filter.Offset)
	}

	err := query.Order(product.OrderClause(filter)).Find(&products).Error
	if err != nil {
		return nil, err
	}

	return products, nil
}
