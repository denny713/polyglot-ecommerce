package supplier

import (
	"context"
	"errors"

	dto "product-service/internal/dto/supplier"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Update implement service for update supplier
func (s service) Update(ctx context.Context, request dto.SupplierUpdateReq) (dto.SupplierUpdateRes, error) {
	orm := s.db.Orm(ctx)

	// Get existing supplier
	existing, err := s.suppliers.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierUpdateRes{}, exception.ErrNotFound
		}

		return dto.SupplierUpdateRes{}, err
	}

	// Update supplier
	supplier, err := s.suppliers.Update(orm, request.ToObjectModel(existing))
	if err != nil {
		return dto.SupplierUpdateRes{}, err
	}

	return dto.ToSupplierUpdateRes(supplier), nil
}
