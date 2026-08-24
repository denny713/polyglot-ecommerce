package supplier

import (
	"context"

	"product-service/internal/configuration"
	dto "product-service/internal/dto/supplier"
	repo "product-service/internal/repository/supplier"
)

// Service is the contract the supplier controller depends on.
type Service interface {
	Create(ctx context.Context, request dto.SupplierCreateReq) (dto.SupplierCreateRes, error)
	Search(ctx context.Context, request dto.SupplierSearchReq) (dto.SupplierSearchRes, error)
	Detail(ctx context.Context, request dto.SupplierDetailReq) (dto.SupplierDetailRes, error)
	Update(ctx context.Context, request dto.SupplierUpdateReq) (dto.SupplierUpdateRes, error)
	Activate(ctx context.Context, request dto.SupplierActivateReq) (dto.SupplierActivateRes, error)
	Deactivate(ctx context.Context, request dto.SupplierDeactivateReq) (dto.SupplierDeactivateRes, error)
	Delete(ctx context.Context, request dto.SupplierDeleteReq) (dto.SupplierDeleteRes, error)
}

type service struct {
	db        configuration.Database
	suppliers repo.Repository
}

// NewService builds the supplier service.
func NewService(db configuration.Database, suppliers repo.Repository) Service {
	return service{db: db, suppliers: suppliers}
}
