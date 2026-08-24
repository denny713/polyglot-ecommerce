package category

import (
	"context"

	"product-service/internal/dto/base"
	dto "product-service/internal/dto/category"
)

// Search implement service for search category
func (s service) Search(ctx context.Context, request dto.CategorySearchReq) (dto.CategorySearchRes, error) {
	orm := s.db.Orm(ctx)

	// Fill the sorting and paging defaults before the query is built
	request = request.Normalize()

	// Search category by the requested filters
	categories, err := s.categories.Search(orm, dto.CategorySearchFilter{
		Name:        request.Name,
		Description: request.Description,
		Paging: base.Paging{
			SortBy:    request.SortBy,
			SortOrder: request.SortOrder,
			Page:      request.Page,
			PageSize:  request.PageSize,
		},
	})
	if err != nil {
		return dto.CategorySearchRes{}, err
	}

	return dto.ToCategorySearchRes(categories), nil
}
