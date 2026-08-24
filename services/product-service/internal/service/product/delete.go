package product

import (
	"context"
	"errors"

	dto "product-service/internal/dto/product"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Delete implement service for delete product
func (s service) Delete(ctx context.Context, request dto.ProductDeleteReq) (dto.ProductDeleteRes, error) {
	orm := s.db.Orm(ctx)

	// Get product detail by parameter
	product, err := s.products.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductDeleteRes{}, exception.ErrNotFound
		}

		return dto.ProductDeleteRes{}, err
	}

	// Delete product
	product.IsDeleted = true
	product.Base = product.Base.Touch()
	_, err = s.products.Update(orm, product)
	if err != nil {
		return dto.ProductDeleteRes{}, err
	}

	return dto.ToProductDeleteRes(product), nil
}
