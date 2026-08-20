package product

import (
	"context"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	productRepo "product-service/internal/repository/product"
)

// Search implement service for search product
func Search(ctx context.Context, request dto.ProductSearchReq) (dto.ProductSearchRes, error) {
	orm := configuration.Orm(ctx)

	// Fill the sorting and paging defaults before the query is built
	request = request.Normalize()

	// Search product by the requested filters
	products, err := productRepo.Search(orm, productRepo.SearchFilter{
		Name:        request.Name,
		Description: request.Description,
		MinPrice:    request.MinPrice,
		MaxPrice:    request.MaxPrice,
		MinStock:    request.MinStock,
		MaxStock:    request.MaxStock,
		SortBy:      request.SortBy,
		SortOrder:   request.SortOrder,
		Limit:       request.Limit(),
		Offset:      request.Offset(),
	})
	if err != nil {
		return dto.ProductSearchRes{}, err
	}

	return dto.ToProductSearchRes(products), nil
}
