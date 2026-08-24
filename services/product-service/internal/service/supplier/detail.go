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

// Detail implement service for get supplier detail
func Detail(ctx context.Context, request dto.SupplierDetailReq) (dto.SupplierDetailRes, error) {
	orm := configuration.Orm(ctx)

	// Get supplier detail by parameter
	supplier, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.SupplierDetailRes{}, exception.ErrNotFound
		}

		return dto.SupplierDetailRes{}, err
	}

	return dto.ToSupplierDetailRes(supplier), nil
}
