package product

import (
	"product-service/internal/dto/base"
	"product-service/internal/dto/product"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// sortColumns maps user-facing sort keys to database column names for product.
var sortColumns = map[string]string{
	"id":         "product.id",
	"name":       "product.name",
	"price":      "product.price",
	"stock":      "stock_position.quantity",
	"created_at": "product.created_at",
	"updated_at": "product.updated_at",
}

// Search implement repository for search product by name, description, price range and stock range
func Search(orm *gorm.DB, filter product.ProductSearchFilter) ([]model.Product, error) {
	products := make([]model.Product, 0)

	query := orm.
		Preload("Category", "is_deleted = FALSE").
		Preload("Supplier", "is_deleted = FALSE").
		Preload("Stock").
		Preload("StockPosition", "is_deleted = FALSE").
		Model(&model.Product{}).
		Select("product.*").
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

	if limit := filter.Paging.Limit(); limit > 0 {
		query = query.Limit(limit)
	}

	if offset := filter.Paging.Offset(); offset > 0 {
		query = query.Offset(offset)
	}

	err := query.Order(base.OrderClause(sortColumns, filter.SortBy, filter.SortOrder)).Find(&products).Error
	if err != nil {
		return nil, err
	}

	return products, nil
}
