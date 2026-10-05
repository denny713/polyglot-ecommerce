package category

import (
	"errors"
	"product-service/internal/constant"
	"product-service/internal/dto/base"
	"product-service/internal/model"
	"strings"
)

type (
	CategorySearchReq struct {
		Name        string
		Description string
		base.Paging
	}

	CategorySearchRes struct {
		Data []CategoryDetailRes `json:"data"`
	}

	CategorySearchFilter struct {
		Name        string
		Description string
		base.Paging
	}
)

// Validate checks the filters make sense before the query is built. Every
// filter is optional, a zero value simply means the filter is not applied.
func (c CategorySearchReq) Validate() error {
	sortAllowed := allowedSortBy()

	if sortBy := strings.ToLower(strings.TrimSpace(c.SortBy)); sortBy != "" && !sortAllowed[sortBy] {
		return errors.New("sort_by must be one of id, name, description, created_at, or updated_at")
	}

	if sortOrder := strings.ToLower(strings.TrimSpace(c.SortOrder)); sortOrder != "" &&
		sortOrder != constant.SortOrderAsc && sortOrder != constant.SortOrderDesc {
		return errors.New("sort_order must be one of asc or desc")
	}

	if c.Page < 0 || c.PageSize < 0 {
		return errors.New("page and page_size must not be negative")
	}

	return nil
}

// Normalize trims the text filters and fills the sorting and paging defaults so
// the repository always receives a ready to use request.
func (c CategorySearchReq) Normalize() CategorySearchReq {
	c.Name = strings.TrimSpace(c.Name)
	c.Description = strings.TrimSpace(c.Description)
	c.Paging = c.Paging.Normalize(allowedSortBy())

	return c
}

// ToCategorySearchRes mapping the table model.Category rows to the response object.
func ToCategorySearchRes(categories []model.Category) CategorySearchRes {
	data := make([]CategoryDetailRes, 0, len(categories))
	for _, category := range categories {
		data = append(data, ToCategoryDetailRes(category))
	}

	return CategorySearchRes{Data: data}
}

// allowedSortBy returns a map of allowed sort fields for the product search.
// This is used to validate the sort_by parameter in the request.
func allowedSortBy() map[string]bool {
	return map[string]bool{
		"id":          true,
		"name":        true,
		"description": true,
		"created_at":  true,
		"updated_at":  true,
	}
}
