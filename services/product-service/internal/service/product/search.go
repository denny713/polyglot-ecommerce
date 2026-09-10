package product

import (
	"context"

	"product-service/internal/dto/base"
	dto "product-service/internal/dto/product"
)

// Search implement service for search product
func (s service) Search(ctx context.Context, request dto.ProductSearchReq) (dto.ProductSearchRes, error) {
	orm := s.db.Orm(ctx)

	// Fill the sorting and paging defaults before the query is built
	request = request.Normalize()

	// Search product by the requested filters
	products, err := s.products.Search(orm, dto.ProductSearchFilter{
		Name:        request.Name,
		Description: request.Description,
		MinPrice:    request.MinPrice,
		MaxPrice:    request.MaxPrice,
		MinStock:    request.MinStock,
		MaxStock:    request.MaxStock,
		Paging: base.Paging{
			SortBy:    request.SortBy,
			SortOrder: request.SortOrder,
			Page:      request.Page,
			PageSize:  request.PageSize,
		},
	})
	if err != nil {
		return dto.ProductSearchRes{}, err
	}

	return dto.ToProductSearchRes(products), nil
}
