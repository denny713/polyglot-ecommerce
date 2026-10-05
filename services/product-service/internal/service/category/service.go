package category

import (
	"context"

	"product-service/internal/configuration"
	dto "product-service/internal/dto/category"
	repo "product-service/internal/repository/category"
)

// Service is the contract the category controller depends on.
type Service interface {
	Create(ctx context.Context, request dto.CategoryCreateReq) (dto.CategoryCreateRes, error)
	Search(ctx context.Context, request dto.CategorySearchReq) (dto.CategorySearchRes, error)
	Detail(ctx context.Context, request dto.CategoryDetailReq) (dto.CategoryDetailRes, error)
	Update(ctx context.Context, request dto.CategoryUpdateReq) (dto.CategoryUpdateRes, error)
	Activate(ctx context.Context, request dto.CategoryActivateReq) (dto.CategoryActivateRes, error)
	Deactivate(ctx context.Context, request dto.CategoryDeactivateReq) (dto.CategoryDeactivateRes, error)
	Delete(ctx context.Context, request dto.CategoryDeleteReq) (dto.CategoryDeleteRes, error)
}

type service struct {
	db         configuration.Database
	categories repo.Repository
}

// NewService builds the category service.
func NewService(db configuration.Database, categories repo.Repository) Service {
	return service{db: db, categories: categories}
}
