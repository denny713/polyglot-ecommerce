package product

import (
	"context"
	"errors"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	productRepo "product-service/internal/repository/product"

	"gorm.io/gorm"
)

// Deactivate implement service for deactivate product
func Deactivate(ctx context.Context, request dto.ProductDeactivateReq) (dto.ProductDeactivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get product detail by parameter
	product, err := productRepo.Detail(orm, "id", request.ID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductDeactivateRes{}, ErrProductNotFound
		}

		return dto.ProductDeactivateRes{}, err
	}

	// Update product status to inactive
	product.IsActive = false
	_, err = productRepo.Update(orm, product)
	if err != nil {
		return dto.ProductDeactivateRes{}, err
	}

	return dto.ProductDeactivateRes{
		ID:     product.ID,
		Name:   product.Name,
		Status: "inactive",
	}, nil
}
