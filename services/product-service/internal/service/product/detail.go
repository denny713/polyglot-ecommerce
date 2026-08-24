package product

import (
	"context"
	"errors"

	dto "product-service/internal/dto/product"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Detail implement service for get product detail
func (s service) Detail(ctx context.Context, request dto.ProductDetailReq) (dto.ProductDetailRes, error) {
	orm := s.db.Orm(ctx)

	// Get product detail by parameter
	product, err := s.products.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductDetailRes{}, exception.ErrNotFound
		}

		return dto.ProductDetailRes{}, err
	}

	return dto.ToProductDetailRes(product), nil
}
