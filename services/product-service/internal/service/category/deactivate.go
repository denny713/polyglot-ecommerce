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

// Deactivate implement service for deactivate category
func Deactivate(ctx context.Context, request dto.CategoryDeactivateReq) (dto.CategoryDeactivateRes, error) {
	orm := configuration.Orm(ctx)

	// Get category detail by parameter
	category, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryDeactivateRes{}, exception.ErrNotFound
		}

		return dto.CategoryDeactivateRes{}, err
	}

	// Check is category already inactive
	if !category.IsActive {
		return dto.CategoryDeactivateRes{}, exception.ErrAlreadyInactive
	}

	// Update category status to inactive
	category.IsActive = false
	category.Base = category.Base.Touch()
	_, err = repo.Update(orm, category)
	if err != nil {
		return dto.CategoryDeactivateRes{}, err
	}

	return dto.ToCategoryDeactivateRes(category), nil
}
