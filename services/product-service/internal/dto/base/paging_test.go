package base

import (
	"testing"

	"product-service/internal/constant"

	"github.com/stretchr/testify/require"
)

func allowed() map[string]bool {
	return map[string]bool{"id": true, "name": true, "created_at": true}
}

func TestNormalize(t *testing.T) {
	tests := []struct {
		name  string
		given Paging
		want  Paging
	}{
		{
			name:  "the defaults fill an empty request",
			given: Paging{},
			want: Paging{
				SortBy:    constant.DefaultSortBy,
				SortOrder: constant.SortOrderDesc,
				Page:      constant.DefaultPage,
				PageSize:  constant.DefaultPageSize,
			},
		},
		{
			name:  "a known sortBy is kept, trimmed and lowered",
			given: Paging{SortBy: "  NAME  ", SortOrder: " ASC ", Page: 3, PageSize: 25},
			want:  Paging{SortBy: "name", SortOrder: constant.SortOrderAsc, Page: 3, PageSize: 25},
		},
		{
			name:  "an unknown sortBy falls back to the default",
			given: Paging{SortBy: "password", Page: 1, PageSize: 1},
			want:  Paging{SortBy: constant.DefaultSortBy, SortOrder: constant.SortOrderDesc, Page: 1, PageSize: 1},
		},
		{
			name:  "an unknown sortOrder falls back to desc",
			given: Paging{SortBy: "id", SortOrder: "sideways", Page: 1, PageSize: 1},
			want:  Paging{SortBy: "id", SortOrder: constant.SortOrderDesc, Page: 1, PageSize: 1},
		},
		{
			name:  "a negative page falls back to the first one",
			given: Paging{SortBy: "id", SortOrder: "asc", Page: -5, PageSize: -5},
			want: Paging{
				SortBy:    "id",
				SortOrder: constant.SortOrderAsc,
				Page:      constant.DefaultPage,
				PageSize:  constant.DefaultPageSize,
			},
		},
		{
			name:  "a page size over the maximum is capped",
			given: Paging{SortBy: "id", SortOrder: "asc", Page: 2, PageSize: constant.MaxPageSize + 1},
			want:  Paging{SortBy: "id", SortOrder: constant.SortOrderAsc, Page: 2, PageSize: constant.MaxPageSize},
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			require.Equal(t, test.want, test.given.Normalize(allowed()))
		})
	}
}

func TestLimitAndOffset(t *testing.T) {
	require.Equal(t, 10, Paging{PageSize: 10}.Limit())
	require.Equal(t, 0, Paging{Page: 1, PageSize: 10}.Offset())
	require.Equal(t, 20, Paging{Page: 3, PageSize: 10}.Offset())
}

func TestOrderClause(t *testing.T) {
	columns := map[string]string{"id": "category.id", "name": "category.name"}

	require.Equal(t, "category.name ASC", OrderClause(columns, "name", "asc"))
	require.Equal(t, "category.name ASC", OrderClause(columns, "name", "ASC"))
	require.Equal(t, "category.name DESC", OrderClause(columns, "name", "desc"))

	// An unknown sortBy falls back to the id column, and an unknown direction
	// falls back to descending.
	require.Equal(t, "category.id DESC", OrderClause(columns, "password", ""))
	require.Equal(t, "category.id DESC", OrderClause(columns, "", "sideways"))
}
