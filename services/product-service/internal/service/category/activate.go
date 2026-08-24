package category

import (
	"context"
	"errors"

	dto "product-service/internal/dto/category"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Activate implement service for activate category
func (s service) Activate(ctx context.Context, request dto.CategoryActivateReq) (dto.CategoryActivateRes, error) {
	orm := s.db.Orm(ctx)

	// Get category detail by parameter
	category, err := s.categories.Detail(orm, "id", request.Id)
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
	_, err = s.categories.Update(orm, category)
	if err != nil {
		return dto.CategoryActivateRes{}, err
	}

	return dto.ToCategoryActivateRes(category), nil
}
