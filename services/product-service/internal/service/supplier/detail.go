package supplier

import (
	"context"
	"errors"

	dto "product-service/internal/dto/supplier"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Detail implement service for get supplier detail
func (s service) Detail(ctx context.Context, request dto.SupplierDetailReq) (dto.SupplierDetailRes, error) {
	orm := s.db.Orm(ctx)

	// Get supplier detail by parameter
	supplier, err := s.suppliers.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierDetailRes{}, exception.ErrNotFound
		}

		return dto.SupplierDetailRes{}, err
	}

	return dto.ToSupplierDetailRes(supplier), nil
}
