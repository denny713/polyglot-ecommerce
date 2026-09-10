package product

import (
	"context"
	"errors"
	dto "product-service/internal/dto/product"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// History implement service for get product detail
func (s service) History(ctx context.Context, request dto.ProductHistoryReq) (dto.ProductHistoryRes, error) {
	orm := s.db.Orm(ctx)

	// Get product detail by parameter
	product, err := s.products.Detail(orm, "id", request.Id, true)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductHistoryRes{}, exception.ErrNotFound
		}

		return dto.ProductHistoryRes{}, err
	}

	return dto.ToProductHistoryRes(product), nil
}
