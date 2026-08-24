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

// Activate implement service for activate supplier
func Activate(ctx context.Context, request dto.SupplierActivateReq) (dto.SupplierActivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get supplier detail by parameter
	supplier, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierActivateRes{}, exception.ErrNotFound
		}

		return dto.SupplierActivateRes{}, err
	}

	// Check is supplier already active
	if supplier.IsActive {
		return dto.SupplierActivateRes{}, exception.ErrAlreadyActive
	}

	// Update supplier status to active
	supplier.IsActive = true
	supplier.Base = supplier.Base.Touch()
	_, err = repo.Update(orm, supplier)
	if err != nil {
		return dto.SupplierActivateRes{}, err
	}

	return dto.ToSupplierActivateRes(supplier), nil
}
