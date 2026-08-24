package category

import (
	"errors"
	"product-service/internal/constant"
	"product-service/internal/model"
	"strings"
)

type (
	CategorySearchReq struct {
		Name        string
		Description string
		SortBy      string
		SortOrder   string
		Page        int
		PageSize    int
	}

	CategorySearchRes struct {
		Data []CategoryDetailRes `json:"data"`
	}

	CategorySearchFilter struct {
		Name        string
		Description string
		SortBy      string
		SortOrder   string
		Limit       int
		Offset      int
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
	sortAllowed := allowedSortBy()
	c.Name = strings.TrimSpace(c.Name)
	c.Description = strings.TrimSpace(c.Description)

	c.SortBy = strings.ToLower(strings.TrimSpace(c.SortBy))
	if !sortAllowed[c.SortBy] {
		c.SortBy = constant.DefaultSortBy
	}

	c.SortOrder = strings.ToLower(strings.TrimSpace(c.SortOrder))
	if c.SortOrder != constant.SortOrderAsc {
		c.SortOrder = constant.SortOrderDesc
	}

	if c.Page <= 0 {
		c.Page = constant.DefaultPage
	}

	if c.PageSize <= 0 {
		c.PageSize = constant.DefaultPageSize
	}

	if c.PageSize > constant.MaxPageSize {
		c.PageSize = constant.MaxPageSize
	}

	return c
}

// Limit is the number of rows a single page holds.
func (c CategorySearchReq) Limit() int {
	return c.PageSize
}

// Offset is the number of rows skipped to reach the requested page.
func (c CategorySearchReq) Offset() int {
	return (c.Page - 1) * c.PageSize
}

// ToCategorySearchRes mapping the table model.Category rows to the response object.
func ToCategorySearchRes(categories []model.Category) CategorySearchRes {
	data := make([]CategoryDetailRes, 0, len(categories))
	for _, category := range categories {
		data = append(data, ToCategoryDetailRes(category))
	}

	return CategorySearchRes{Data: data}
}

// OrderClause builds the order clause of the search, an unknown sort field falls
// back to the newest product first.
func OrderClause(filter CategorySearchFilter) string {
	sortColumns := map[string]string{
		"id":          "category.id",
		"name":        "category.name",
		"description": "category.description",
		"created_at":  "category.created_at",
		"updated_at":  "category.updated_at",
	}

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
