package category

import (
	"context"
	"errors"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/category"
	"product-service/internal/exception"
	repo "product-service/internal/repository/category"

	"gorm.io/gorm"
)

// Activate implement service for activate category
func Activate(ctx context.Context, request dto.CategoryActivateReq) (dto.CategoryActivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get category detail by parameter
	category, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryActivateRes{}, exception.ErrNotFound
		}

		return dto.CategoryActivateRes{}, err
	}

	// Check is category already active
	if category.IsActive {
		return dto.CategoryActivateRes{}, exception.ErrAlreadyActive
	}

	// Update category status to active
	category.IsActive = true
	category.Base = category.Base.Touch()
	_, err = repo.Update(orm, category)
	if err != nil {
		return dto.CategoryActivateRes{}, err
	}

	return dto.ToCategoryActivateRes(category), nil
}
