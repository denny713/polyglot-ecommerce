package base

import (
	"product-service/internal/constant"
	"strings"
)

type Paging struct {
	SortBy    string
	SortOrder string
	Page      int
	PageSize  int
}

// Normalize fills the default values for sorting and paging, and ensures the requested sortBy is allowed.
// If not, it falls back to the default sortBy.
func (p Paging) Normalize(allowedSortBy map[string]bool) Paging {
	p.SortBy = strings.ToLower(strings.TrimSpace(p.SortBy))
	if !allowedSortBy[p.SortBy] {
		p.SortBy = constant.DefaultSortBy
	}

	p.SortOrder = strings.ToLower(strings.TrimSpace(p.SortOrder))
	if p.SortOrder != constant.SortOrderAsc {
		p.SortOrder = constant.SortOrderDesc
	}

	if p.Page <= 0 {
		p.Page = constant.DefaultPage
	}

	if p.PageSize <= 0 {
		p.PageSize = constant.DefaultPageSize
	}

	if p.PageSize > constant.MaxPageSize {
		p.PageSize = constant.MaxPageSize
	}

	return p
}

// Limit is the number of rows a single page holds.
func (p Paging) Limit() int {
	return p.PageSize
}

// Offset is the number of rows skipped to reach the requested page.
func (p Paging) Offset() int {
	return (p.Page - 1) * p.PageSize
}

// OrderClause returns the SQL ORDER BY clause based on the provided sortColumns mapping, sortBy, and sortOrder.
func OrderClause(sortColumns map[string]string, sortBy, sortOrder string) string {
	column, ok := sortColumns[sortBy]
	if !ok {
		column = sortColumns["id"]
	}

	direction := "DESC"
	if strings.EqualFold(sortOrder, "asc") {
		direction = "ASC"
	}

	return column + " " + direction
}
