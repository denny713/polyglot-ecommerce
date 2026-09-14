package category

import (
	"context"
	"errors"

	dto "product-service/internal/dto/category"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Update implement service for update category
func (s service) Update(ctx context.Context, request dto.CategoryUpdateReq) (dto.CategoryUpdateRes, error) {
	orm := s.db.Orm(ctx)

	// Get existing category
	existing, err := s.categories.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryUpdateRes{}, exception.ErrNotFound
		}

		return dto.CategoryUpdateRes{}, err
	}

	// Update category
	category, err := s.categories.Update(orm, request.ToObjectModel(ctx, existing))
	if err != nil {
		return dto.CategoryUpdateRes{}, err
	}

	return dto.ToCategoryUpdateRes(category), nil
}
