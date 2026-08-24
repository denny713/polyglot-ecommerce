package supplier

import (
	"context"
	"product-service/internal/configuration"
	"product-service/internal/dto/base"
	dto "product-service/internal/dto/supplier"
	repo "product-service/internal/repository/supplier"
)

// Search implement service for search supplier
func Search(ctx context.Context, request dto.SupplierSearchReq) (dto.SupplierSearchRes, error) {
	orm := configuration.Orm(ctx)

	// Fill the sorting and paging defaults before the query is built
	request = request.Normalize()

	// Search supplier by the requested filters
	suppliers, err := repo.Search(orm, dto.SupplierSearchFilter{
		Name:          request.Name,
		Phone:         request.Phone,
		Email:         request.Email,
		ContactPerson: request.ContactPerson,
		Province:      request.Province,
		City:          request.City,
		District:      request.District,
		Subdistrict:   request.Subdistrict,
		PostalCode:    request.PostalCode,
		Paging: base.Paging{
			SortBy:    request.SortBy,
			SortOrder: request.SortOrder,
			Page:      request.Page,
			PageSize:  request.PageSize,
		},
	})
	if err != nil {
		return dto.SupplierSearchRes{}, err
	}

	return dto.ToSupplierSearchRes(suppliers), nil
}
