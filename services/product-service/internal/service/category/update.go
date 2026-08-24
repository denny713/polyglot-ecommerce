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

// Update implement service for update category
func Update(ctx context.Context, request dto.CategoryUpdateReq) (dto.CategoryUpdateRes, error) {
	orm := configuration.Orm(ctx)

	// Get existing category
	existing, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryUpdateRes{}, exception.ErrNotFound
		}

		return dto.CategoryUpdateRes{}, err
	}

	// Update category
	category, err := repo.Update(orm, request.ToObjectModel(existing))
	if err != nil {
		return dto.CategoryUpdateRes{}, err
	}

	return dto.ToCategoryUpdateRes(category), nil
}
