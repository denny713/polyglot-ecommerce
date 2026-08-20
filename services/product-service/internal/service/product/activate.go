package product

import (
	"context"
	"errors"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	productRepo "product-service/internal/repository/product"

	"gorm.io/gorm"
)

// Activate implement service for activate product
func Activate(ctx context.Context, request dto.ProductActivateReq) (dto.ProductActivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get product detail by parameter
	product, err := productRepo.Detail(orm, "id", request.ID)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductActivateRes{}, ErrProductNotFound
		}

		return dto.ProductActivateRes{}, err
	}

	// Update product status to active
	product.IsActive = true
	_, err = productRepo.Update(orm, product)
	if err != nil {
		return dto.ProductActivateRes{}, err
	}

	return dto.ProductActivateRes{
		ID:     product.ID,
		Name:   product.Name,
		Status: "active",
	}, nil
}
