package product

import (
	"context"
	"errors"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	"product-service/internal/exception"
	repo "product-service/internal/repository/product"

	"gorm.io/gorm"
)

// Deactivate implement service for deactivate product
func Deactivate(ctx context.Context, request dto.ProductDeactivateReq) (dto.ProductDeactivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get product detail by parameter
	product, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductDeactivateRes{}, exception.ErrNotFound
		}

		return dto.ProductDeactivateRes{}, err
	}

	// Check is product already inactive
	if !product.IsActive {
		return dto.ProductDeactivateRes{}, exception.ErrAlreadyInactive
	}

	// Update product status to inactive
	product.IsActive = false
	_, err = repo.Update(orm, product)
	if err != nil {
		return dto.ProductDeactivateRes{}, err
	}

	return dto.ToProductDeactivateRes(product), nil
}
