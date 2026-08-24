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

// Update implement service for update supplier
func Update(ctx context.Context, request dto.SupplierUpdateReq) (dto.SupplierUpdateRes, error) {
	orm := configuration.Orm(ctx)

	// Get existing supplier
	existing, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierUpdateRes{}, exception.ErrNotFound
		}

		return dto.SupplierUpdateRes{}, err
	}

	// Update supplier
	supplier, err := repo.Update(orm, request.ToObjectModel(existing))
	if err != nil {
		return dto.SupplierUpdateRes{}, err
	}

	return dto.ToSupplierUpdateRes(supplier), nil
}
