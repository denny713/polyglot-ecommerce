package category

import (
	"context"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/category"
	repo "product-service/internal/repository/category"
)

// Create implement service for create new category
func Create(ctx context.Context, request dto.CategoryCreateReq) (dto.CategoryCreateRes, error) {
	orm := configuration.Orm(ctx)

	// Submit new category
	category, err := repo.Create(orm, request.ToObjectModel())
	if err != nil {
		return dto.CategoryCreateRes{}, err
	}

	return dto.ToResponse(category), nil
}
