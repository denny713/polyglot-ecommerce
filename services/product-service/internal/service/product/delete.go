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

// Delete implement service for delete product
func Delete(ctx context.Context, request dto.ProductDeleteReq) (dto.ProductDeleteRes, error) {
	orm := configuration.Orm(ctx)

	// Get product detail by parameter
	product, err := productRepo.Detail(orm, "id", request.ID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductDeleteRes{}, exception.ErrProductNotFound
		}

		return dto.ProductDeleteRes{}, err
	}

	// Delete product
	product.IsDeleted = true
	_, err = productRepo.Update(orm, product)
	if err != nil {
		return dto.ProductDeleteRes{}, err
	}

	return dto.ProductDeleteRes{
		ID:     product.ID,
		Name:   product.Name,
		Status: "deleted",
	}, nil
}
