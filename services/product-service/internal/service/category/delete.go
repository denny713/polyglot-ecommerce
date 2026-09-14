package category

import (
	"context"
	"errors"

	dto "product-service/internal/dto/category"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Delete implement service for delete category
func (s service) Delete(ctx context.Context, request dto.CategoryDeleteReq) (dto.CategoryDeleteRes, error) {
	orm := s.db.Orm(ctx)

	// Get category detail by parameter
	category, err := s.categories.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryDeleteRes{}, exception.ErrNotFound
		}

		return dto.CategoryDeleteRes{}, err
	}

	// Delete category
	category.IsDeleted = true
	category.Base = category.Base.Touch(ctx)
	_, err = s.categories.Update(orm, category)
	if err != nil {
		return dto.CategoryDeleteRes{}, err
	}

	return dto.ToCategoryDeleteRes(category), nil
}
