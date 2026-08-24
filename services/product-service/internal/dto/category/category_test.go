package category

import (
	"testing"
	"time"

	"product-service/internal/constant"
	"product-service/internal/dto/base"
	"product-service/internal/model"

	"github.com/stretchr/testify/require"
)

func sampleCategory() model.Category {
	created := time.Date(2024, time.January, 2, 3, 4, 5, 0, time.UTC)

	return model.Category{
		Id:          7,
		Name:        "Elektronik",
		Description: "Perangkat elektronik",
		Base: model.Base{
			IsActive:  true,
			IsDeleted: false,
			CreatedAt: created,
			UpdatedAt: created.Add(time.Hour),
		},
	}
}

func TestCreateReqToObjectModel(t *testing.T) {
	before := time.Now()
	got := CategoryCreateReq{Name: "Elektronik", Description: "Perangkat"}.ToObjectModel()

	require.Equal(t, "Elektronik", got.Name)
	require.Equal(t, "Perangkat", got.Description)
	require.True(t, got.IsActive)
	require.False(t, got.IsDeleted)
	require.False(t, got.CreatedAt.Before(before))
}

func TestCreateReqValidate(t *testing.T) {
	require.NoError(t, CategoryCreateReq{Name: "Elektronik"}.Validate())
	require.EqualError(t, CategoryCreateReq{}.Validate(), "name is required")
}

func TestToResponse(t *testing.T) {
	category := sampleCategory()
	got := ToResponse(category)

	require.Equal(t, CategoryCreateRes{
		Id:          7,
		Name:        "Elektronik",
		Description: "Perangkat elektronik",
		IsActive:    true,
		CreatedAt:   category.CreatedAt,
		UpdatedAt:   category.UpdatedAt,
	}, got)
}

func TestToCategoryDetailRes(t *testing.T) {
	category := sampleCategory()
	category.IsDeleted = true

	got := ToCategoryDetailRes(category)

	require.Equal(t, CategoryDetailRes{
		Id:          7,
		Name:        "Elektronik",
		Description: "Perangkat elektronik",
		IsActive:    true,
		IsDeleted:   true,
		CreatedAt:   category.CreatedAt,
		UpdatedAt:   category.UpdatedAt,
	}, got)
}

func TestStatusMappers(t *testing.T) {
	category := sampleCategory()

	require.Equal(t, CategoryActivateRes{Id: 7, Name: "Elektronik", Status: constant.Active},
		ToCategoryActivateRes(category))
	require.Equal(t, CategoryDeactivateRes{Id: 7, Name: "Elektronik", Status: constant.Inactive},
		ToCategoryDeactivateRes(category))
	require.Equal(t, CategoryDeleteRes{Id: 7, Name: "Elektronik", Status: constant.Delete},
		ToCategoryDeleteRes(category))
}

func TestUpdateReqToObjectModel(t *testing.T) {
	existing := sampleCategory()
	existing.CreatedBy = 42
	existing.UpdatedBy = 42

	before := time.Now()
	got := CategoryUpdateReq{Id: 7, Name: "Baru", Description: "Deskripsi baru"}.ToObjectModel(existing)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Baru", got.Name)
	require.Equal(t, "Deskripsi baru", got.Description)

	// The status flags and the creation trail of the existing row survive.
	require.True(t, got.IsActive)
	require.Equal(t, int64(42), got.CreatedBy)
	require.Equal(t, existing.CreatedAt, got.CreatedAt)
	require.Equal(t, int64(1), got.UpdatedBy)
	require.False(t, got.UpdatedAt.Before(before))
}

func TestUpdateReqValidate(t *testing.T) {
	require.NoError(t, CategoryUpdateReq{Id: 1, Name: "Elektronik"}.Validate())
	require.EqualError(t, CategoryUpdateReq{Name: "Elektronik"}.Validate(), "id is required")
	require.EqualError(t, CategoryUpdateReq{Id: 1}.Validate(), "name is required")
}

func TestToCategoryUpdateRes(t *testing.T) {
	category := sampleCategory()

	require.Equal(t, CategoryUpdateRes{
		Id:          7,
		Name:        "Elektronik",
		Description: "Perangkat elektronik",
		IsActive:    true,
		CreatedAt:   category.CreatedAt,
		UpdatedAt:   category.UpdatedAt,
	}, ToCategoryUpdateRes(category))
}

func TestSearchReqValidate(t *testing.T) {
	tests := []struct {
		name    string
		given   CategorySearchReq
		wantErr string
	}{
		{name: "an empty request applies no filter", given: CategorySearchReq{}},
		{
			name:  "a known sortBy is accepted whatever its casing",
			given: CategorySearchReq{Paging: base.Paging{SortBy: " NAME ", SortOrder: " ASC "}},
		},
		{
			name:    "an unknown sortBy is rejected",
			given:   CategorySearchReq{Paging: base.Paging{SortBy: "password"}},
			wantErr: "sort_by must be one of id, name, description, created_at, or updated_at",
		},
		{
			name:    "an unknown sortOrder is rejected",
			given:   CategorySearchReq{Paging: base.Paging{SortOrder: "sideways"}},
			wantErr: "sort_order must be one of asc or desc",
		},
		{
			name:    "a negative page is rejected",
			given:   CategorySearchReq{Paging: base.Paging{Page: -1}},
			wantErr: "page and page_size must not be negative",
		},
		{
			name:    "a negative page size is rejected",
			given:   CategorySearchReq{Paging: base.Paging{PageSize: -1}},
			wantErr: "page and page_size must not be negative",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := test.given.Validate()
			if test.wantErr == "" {
				require.NoError(t, err)

				return
			}

			require.EqualError(t, err, test.wantErr)
		})
	}
}

func TestSearchReqNormalize(t *testing.T) {
	got := CategorySearchReq{
		Name:        "  elektronik  ",
		Description: "  perangkat  ",
		Paging:      base.Paging{SortBy: "NAME", SortOrder: "ASC", Page: 2, PageSize: 5},
	}.Normalize()

	require.Equal(t, "elektronik", got.Name)
	require.Equal(t, "perangkat", got.Description)
	require.Equal(t, "name", got.SortBy)
	require.Equal(t, constant.SortOrderAsc, got.SortOrder)
	require.Equal(t, 2, got.Page)
	require.Equal(t, 5, got.PageSize)
}

func TestToCategorySearchRes(t *testing.T) {
	got := ToCategorySearchRes([]model.Category{sampleCategory(), {Id: 8, Name: "Baju"}})

	require.Len(t, got.Data, 2)
	require.Equal(t, int64(7), got.Data[0].Id)
	require.Equal(t, "Baju", got.Data[1].Name)

	// An empty result is an empty list, never a nil one, so the json body of the
	// response carries [] instead of null.
	require.NotNil(t, ToCategorySearchRes(nil).Data)
	require.Empty(t, ToCategorySearchRes(nil).Data)
}

func TestAllowedSortBy(t *testing.T) {
	allowed := allowedSortBy()

	for _, field := range []string{"id", "name", "description", "created_at", "updated_at"} {
		require.True(t, allowed[field], field)
	}

	require.False(t, allowed["password"])
}
