package supplier

import (
	"context"
	"errors"

	dto "product-service/internal/dto/supplier"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Delete implement service for delete supplier
func (s service) Delete(ctx context.Context, request dto.SupplierDeleteReq) (dto.SupplierDeleteRes, error) {
	orm := s.db.Orm(ctx)

	// Get supplier detail by parameter
	supplier, err := s.suppliers.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierDeleteRes{}, exception.ErrNotFound
		}

		return dto.SupplierDeleteRes{}, err
	}

	// Delete supplier
	supplier.IsDeleted = true
	supplier.Base = supplier.Base.Touch()
	_, err = s.suppliers.Update(orm, supplier)
	if err != nil {
		return dto.SupplierDeleteRes{}, err
	}

	return dto.ToSupplierDeleteRes(supplier), nil
}
