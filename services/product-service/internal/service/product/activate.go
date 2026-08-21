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

// Activate implement service for activate product
func Activate(ctx context.Context, request dto.ProductActivateReq) (dto.ProductActivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get product detail by parameter
	product, err := productRepo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductActivateRes{}, exception.ErrProductNotFound
		}

		return dto.ProductActivateRes{}, err
	}

	// Check is product already active
	if product.IsActive {
		return dto.ProductActivateRes{}, exception.ErrProductAlreadyActive
	}

	// Update product status to active
	product.IsActive = true
	_, err = productRepo.Update(orm, product)
	if err != nil {
		return dto.ProductActivateRes{}, err
	}

	return dto.ProductActivateRes{
		Id:     product.Id,
		Name:   product.Name,
		Status: "active",
	}, nil
}
