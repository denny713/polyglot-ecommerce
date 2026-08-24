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

// Delete implement service for delete supplier
func Delete(ctx context.Context, request dto.SupplierDeleteReq) (dto.SupplierDeleteRes, error) {
	orm := configuration.Orm(ctx)

	// Get supplier detail by parameter
	supplier, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierDeleteRes{}, exception.ErrNotFound
		}

		return dto.SupplierDeleteRes{}, err
	}

	// Delete supplier
	supplier.IsDeleted = true
	supplier.Base = supplier.Base.Touch()
	_, err = repo.Update(orm, supplier)
	if err != nil {
		return dto.SupplierDeleteRes{}, err
	}

	return dto.ToSupplierDeleteRes(supplier), nil
}
