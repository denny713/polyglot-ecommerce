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

func Detail(ctx context.Context, request dto.CategoryDetailReq) (dto.CategoryDetailRes, error) {
	orm := configuration.Orm(ctx)

	// Get category detail by parameter
	category, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryDetailRes{}, exception.ErrNotFound
		}

		return dto.CategoryDetailRes{}, err
	}

	return dto.ToCategoryDetailRes(category), nil
}
