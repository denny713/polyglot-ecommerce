package product

import (
	"context"
	"errors"

	dto "product-service/internal/dto/product"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Activate implement service for activate product
func (s service) Activate(ctx context.Context, request dto.ProductActivateReq) (dto.ProductActivateRes, error) {
	orm := s.db.Orm(ctx)

	// Get product detail by parameter
	product, err := s.products.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductActivateRes{}, exception.ErrNotFound
		}

		return dto.ProductActivateRes{}, err
	}

	// Check is product already active
	if product.IsActive {
		return dto.ProductActivateRes{}, exception.ErrAlreadyActive
	}

	// Update product status to active
	product.IsActive = true
	product.Base = product.Base.Touch()
	_, err = s.products.Update(orm, product)
	if err != nil {
		return dto.ProductActivateRes{}, err
	}

	return dto.ToProductActivateRes(product), nil
}
