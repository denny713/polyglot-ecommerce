package supplier

import (
	"context"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/supplier"
	repo "product-service/internal/repository/supplier"
)

// Create implement service for create new supplier
func Create(ctx context.Context, request dto.SupplierCreateReq) (dto.SupplierCreateRes, error) {
	orm := configuration.Orm(ctx)

	// Submit new supplier
	supplier, err := repo.Create(orm, request.ToObjectModel())
	if err != nil {
		return dto.SupplierCreateRes{}, err
	}

	return dto.ToResponse(supplier), nil
}
