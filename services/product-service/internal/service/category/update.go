package category

import (
	"context"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/category"
	repo "product-service/internal/repository/category"
)

// Update implement service for update category
func Update(ctx context.Context, request dto.CategoryUpdateReq) (dto.CategoryUpdateRes, error) {
	orm := configuration.Orm(ctx)

	// Get existing category
	category, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		return dto.CategoryUpdateRes{}, err
	}

	// Update category
	category, err = repo.Update(orm, request.ToObjectModel())
	if err != nil {
		return dto.CategoryUpdateRes{}, err
	}

	return dto.ToCategoryUpdateRes(category), nil
}
