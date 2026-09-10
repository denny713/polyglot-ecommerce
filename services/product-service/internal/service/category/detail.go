package category

import (
	"context"
	"errors"

	dto "product-service/internal/dto/category"
	"product-service/internal/exception"

	"gorm.io/gorm"
)

// Detail implement service for get category detail
func (s service) Detail(ctx context.Context, request dto.CategoryDetailReq) (dto.CategoryDetailRes, error) {
	orm := s.db.Orm(ctx)

	// Get category detail by parameter
	category, err := s.categories.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryDetailRes{}, exception.ErrNotFound
		}

		return dto.CategoryDetailRes{}, err
	}

	return dto.ToCategoryDetailRes(category), nil
}
