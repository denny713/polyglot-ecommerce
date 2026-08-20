package product

import (
	"context"
	"errors"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	"product-service/internal/exception"
	productRepo "product-service/internal/repository/product"

	"gorm.io/gorm"
)

// Detail implement service for get product detail
func Detail(ctx context.Context, request dto.ProductDetailReq) (dto.ProductDetailRes, error) {
	orm := configuration.Orm(ctx)

	// Get product detail by parameter
	product, err := productRepo.Detail(orm, "id", request.ID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductDetailRes{}, exception.ErrProductNotFound
		}

		return dto.ProductDetailRes{}, err
	}

	return dto.ToProductDetailRes(product), nil
}
