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

// Delete implement service for delete product
func Delete(ctx context.Context, request dto.ProductDeleteReq) (dto.ProductDeleteRes, error) {
	orm := configuration.Orm(ctx)

	// Get product detail by parameter
	product, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductDeleteRes{}, exception.ErrNotFound
		}

		return dto.ProductDeleteRes{}, err
	}

	// Delete product
	product.IsDeleted = true
	product.Base = product.Base.Touch()
	_, err = repo.Update(orm, product)
	if err != nil {
		return dto.ProductDeleteRes{}, err
	}

	return dto.ToProductDeleteRes(product), nil
}
