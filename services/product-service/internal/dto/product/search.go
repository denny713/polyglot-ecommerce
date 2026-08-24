package product

import (
	"errors"
	"product-service/internal/constant"
	"product-service/internal/dto/base"
	"product-service/internal/model"
	"strings"

	"github.com/shopspring/decimal"
)

type (
	ProductSearchReq struct {
		Name        string
		Description string
		MinPrice    decimal.Decimal
		MaxPrice    decimal.Decimal
		MinStock    int
		MaxStock    int
		base.Paging
	}

	ProductSearchRes struct {
		Data []ProductDetailRes `json:"data"`
	}

	ProductSearchFilter struct {
		Name        string
		Description string
		MinPrice    decimal.Decimal
		MaxPrice    decimal.Decimal
		MinStock    int
		MaxStock    int
		base.Paging
	}
)

// Validate checks the filters make sense before the query is built. Every
// filter is optional, a zero value simply means the filter is not applied.
func (p ProductSearchReq) Validate() error {
	sortAllowed := allowedSortBy()

	if p.MinPrice.IsNegative() || p.MaxPrice.IsNegative() {
		return errors.New("price filter must not be negative")
	}

	if p.MaxPrice.IsPositive() && p.MinPrice.GreaterThan(p.MaxPrice) {
		return errors.New("min_price must not be greater than max_price")
	}

	if p.MinStock < 0 || p.MaxStock < 0 {
		return errors.New("stock filter must not be negative")
	}

	if p.MaxStock > 0 && p.MinStock > p.MaxStock {
		return errors.New("min_stock must not be greater than max_stock")
	}

	if sortBy := strings.ToLower(strings.TrimSpace(p.SortBy)); sortBy != "" && !sortAllowed[sortBy] {
		return errors.New("sort_by must be one of id, name, price, stock, created_at, or updated_at")
	}

	if sortOrder := strings.ToLower(strings.TrimSpace(p.SortOrder)); sortOrder != "" &&
		sortOrder != constant.SortOrderAsc && sortOrder != constant.SortOrderDesc {
		return errors.New("sort_order must be one of asc or desc")
	}

	if p.Page < 0 || p.PageSize < 0 {
		return errors.New("page and page_size must not be negative")
	}

	return nil
}

// Normalize trims the text filters and fills the sorting and paging defaults so
// the repository always receives a ready to use request.
func (p ProductSearchReq) Normalize() ProductSearchReq {
	p.Name = strings.TrimSpace(p.Name)
	p.Description = strings.TrimSpace(p.Description)
	p.Paging = p.Paging.Normalize(allowedSortBy())

	return p
}

// ToProductSearchRes mapping the table model.Product rows to the response object.
func ToProductSearchRes(products []model.Product) ProductSearchRes {
	data := make([]ProductDetailRes, 0, len(products))
	for _, product := range products {
		data = append(data, ToProductDetailRes(product))
	}

	return ProductSearchRes{Data: data}
}

// allowedSortBy returns a map of allowed sort fields for the product search.
// This is used to validate the sort_by parameter in the request.
func allowedSortBy() map[string]bool {
	return map[string]bool{
		"id":         true,
		"name":       true,
		"price":      true,
		"stock":      true,
		"created_at": true,
		"updated_at": true,
	}
}
