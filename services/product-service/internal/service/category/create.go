package category

import (
	"context"

	dto "product-service/internal/dto/category"
)

// Create implement service for create new category
func (s service) Create(ctx context.Context, request dto.CategoryCreateReq) (dto.CategoryCreateRes, error) {
	orm := s.db.Orm(ctx)

	// Submit new category
	category, err := s.categories.Create(orm, request.ToObjectModel())
	if err != nil {
		return dto.CategoryCreateRes{}, err
	}

	return dto.ToResponse(category), nil
}
