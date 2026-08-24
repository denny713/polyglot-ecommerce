package category

import (
	"context"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/category"
	repo "product-service/internal/repository/category"
)

// Search implement service for search category
func Search(ctx context.Context, request dto.CategorySearchReq) (dto.CategorySearchRes, error) {
	orm := configuration.Orm(ctx)

	// Fill the sorting and paging defaults before the query is built
	request = request.Normalize()

	// Search category by the requested filters
	categories, err := repo.Search(orm, dto.CategorySearchFilter{
		Name:        request.Name,
		Description: request.Description,
		SortBy:      request.SortBy,
		SortOrder:   request.SortOrder,
		Limit:       request.Limit(),
		Offset:      request.Offset(),
	})
	if err != nil {
		return dto.CategorySearchRes{}, err
	}

	return dto.ToCategorySearchRes(categories), nil
}
