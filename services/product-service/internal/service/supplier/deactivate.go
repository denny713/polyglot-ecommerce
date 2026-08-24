package supplier

import (
	"context"
	"errors"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/supplier"
	"product-service/internal/exception"
	repo "product-service/internal/repository/supplier"

	"gorm.io/gorm"
)

// Deactivate implement service for deactivate supplier
func Deactivate(ctx context.Context, request dto.SupplierDeactivateReq) (dto.SupplierDeactivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get supplier detail by parameter
	supplier, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierDeactivateRes{}, exception.ErrNotFound
		}

		return dto.SupplierDeactivateRes{}, err
	}

	// Check is supplier already inactive
	if !supplier.IsActive {
		return dto.SupplierDeactivateRes{}, exception.ErrAlreadyInactive
	}

	// Update supplier status to inactive
	supplier.IsActive = false
	supplier.Base = supplier.Base.Touch()
	_, err = repo.Update(orm, supplier)
	if err != nil {
		return dto.SupplierDeactivateRes{}, err
	}

	return dto.ToSupplierDeactivateRes(supplier), nil
}
