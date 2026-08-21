package product

import (
	"product-service/internal/model"
	"strings"

	"github.com/shopspring/decimal"
	"gorm.io/gorm"
)

// SearchFilter carries the filters of the product search. Every field is
// optional, a zero value keeps its condition out of the query.
type SearchFilter struct {
	Name        string
	Description string
	MinPrice    decimal.Decimal
	MaxPrice    decimal.Decimal
	MinStock    int
	MaxStock    int
	SortBy      string
	SortOrder   string
	Limit       int
	Offset      int
}

// sortStock is the only sort field living on the related stock table.
const sortStock = "stock"

// sortColumns maps the logical sort fields to the real columns, the order clause
// is built from this whitelist so no caller value ever reaches the query.
var sortColumns = map[string]string{
	"id":         "product.id",
	"name":       "product.name",
	"price":      "product.price",
	"created_at": "product.created_at",
	"updated_at": "product.updated_at",
}

// Search implement repository for search product by name, description, price
// range and stock range
func Search(orm *gorm.DB, filter SearchFilter) ([]model.Product, error) {
	products := make([]model.Product, 0)

	query := orm.Model(&model.Product{}).
		Select("product.*").
		Preload("Stock").
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

	// The stock quantity lives on the related table, so it is joined only when
	// the request filters or sorts on it.
	if filter.MinStock > 0 || filter.MaxStock > 0 || filter.SortBy == sortStock {
		query = query.Joins("JOIN stock ON stock.product_id = product.id")
	}

	if filter.MinStock > 0 {
		query = query.Where("stock.quantity >= ?", filter.MinStock)
	}

	if filter.MaxStock > 0 {
		query = query.Where("stock.quantity <= ?", filter.MaxStock)
	}

	if filter.Limit > 0 {
		query = query.Limit(filter.Limit)
	}

	if filter.Offset > 0 {
		query = query.Offset(filter.Offset)
	}

	err := query.Order(orderClause(filter)).Find(&products).Error
	if err != nil {
		return nil, err
	}

	return products, nil
}

// orderClause builds the order clause of the search, an unknown sort field falls
// back to the newest product first.
func orderClause(filter SearchFilter) string {
	column, ok := sortColumns[filter.SortBy]
	if !ok {
		column = sortColumns["id"]
	}

	direction := "DESC"
	if strings.EqualFold(filter.SortOrder, "asc") {
		direction = "ASC"
	}

	return column + " " + direction
}
