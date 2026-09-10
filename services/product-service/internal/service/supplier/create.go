package supplier

import (
	"context"

	dto "product-service/internal/dto/supplier"
)

// Create implement service for create new supplier
func (s service) Create(ctx context.Context, request dto.SupplierCreateReq) (dto.SupplierCreateRes, error) {
	orm := s.db.Orm(ctx)

	// Submit new supplier
	supplier, err := s.suppliers.Create(orm, request.ToObjectModel())
	if err != nil {
		return dto.SupplierCreateRes{}, err
	}

	return dto.ToResponse(supplier), nil
}
