package product

import (
	"context"
	"errors"

	dto "product-service/internal/dto/product"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Deactivate implement service for deactivate product
func (s service) Deactivate(ctx context.Context, request dto.ProductDeactivateReq) (dto.ProductDeactivateRes, error) {
	orm := s.db.Orm(ctx)

	// Get product detail by parameter
	product, err := s.products.Detail(orm, "id", request.Id)
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
	product.Base = product.Base.Touch()
	_, err = s.products.Update(orm, product)
	if err != nil {
		return dto.ProductDeactivateRes{}, err
	}

	return dto.ToProductDeactivateRes(product), nil
}
