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

// Delete implement service for delete category
func Delete(ctx context.Context, request dto.CategoryDeleteReq) (dto.CategoryDeleteRes, error) {
	orm := configuration.Orm(ctx)

	// Get category detail by parameter
	category, err := repo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.CategoryDeleteRes{}, exception.ErrNotFound
		}

		return dto.CategoryDeleteRes{}, err
	}

	// Delete category
	category.IsDeleted = true
	_, err = repo.Update(orm, category)
	if err != nil {
		return dto.CategoryDeleteRes{}, err
	}

	return dto.ToCategoryDeleteRes(category), nil
}
