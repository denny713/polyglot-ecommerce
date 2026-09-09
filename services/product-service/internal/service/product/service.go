package product

import (
	"context"

	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	categoryRepo "product-service/internal/repository/category"
	productRepo "product-service/internal/repository/product"
	stockRepo "product-service/internal/repository/stock"
	storageRepo "product-service/internal/repository/storage"
	supplierRepo "product-service/internal/repository/supplier"
)

// Service is the contract the product controller depends on.
type Service interface {
	Create(ctx context.Context, request dto.ProductCreateReq) (dto.ProductCreateRes, error)
	Search(ctx context.Context, request dto.ProductSearchReq) (dto.ProductSearchRes, error)
	Detail(ctx context.Context, request dto.ProductDetailReq) (dto.ProductDetailRes, error)
	History(ctx context.Context, request dto.ProductHistoryReq) (dto.ProductHistoryRes, error)
	Update(ctx context.Context, request dto.ProductUpdateReq) (dto.ProductUpdateRes, error)
	Activate(ctx context.Context, request dto.ProductActivateReq) (dto.ProductActivateRes, error)
	Deactivate(ctx context.Context, request dto.ProductDeactivateReq) (dto.ProductDeactivateRes, error)
	Delete(ctx context.Context, request dto.ProductDeleteReq) (dto.ProductDeleteRes, error)
}

type service struct {
	db            configuration.Database
	products      productRepo.Repository
	categories    categoryRepo.Repository
	suppliers     supplierRepo.Repository
	stockPosition stockRepo.Repository
	storage       storageRepo.Storage
}

// NewService builds the product service.
func NewService(
	db configuration.Database,
	products productRepo.Repository,
	categories categoryRepo.Repository,
	suppliers supplierRepo.Repository,
	stockPosition stockRepo.Repository,
	storage storageRepo.Storage,
) Service {
	return service{
		db:            db,
		products:      products,
		categories:    categories,
		suppliers:     suppliers,
		stockPosition: stockPosition,
		storage:       storage,
	}
}

// removeUploadedImage cleans up the object so a failed write does not leave an
// orphan file in the bucket. The cleanup error is intentionally ignored, the
// original error is the one worth returning to the caller.
func (s service) removeUploadedImage(ctx context.Context, objectName string) {
	if objectName == "" {
		return
	}

	_ = s.storage.Remove(ctx, objectName)
}
