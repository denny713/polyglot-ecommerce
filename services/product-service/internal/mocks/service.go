package mocks

import (
	"context"

	categoryDto "product-service/internal/dto/category"
	productDto "product-service/internal/dto/product"
	supplierDto "product-service/internal/dto/supplier"
)

// CategoryService is a double of the category service.
type CategoryService struct {
	CreateFn     func(ctx context.Context, request categoryDto.CategoryCreateReq) (categoryDto.CategoryCreateRes, error)
	SearchFn     func(ctx context.Context, request categoryDto.CategorySearchReq) (categoryDto.CategorySearchRes, error)
	DetailFn     func(ctx context.Context, request categoryDto.CategoryDetailReq) (categoryDto.CategoryDetailRes, error)
	UpdateFn     func(ctx context.Context, request categoryDto.CategoryUpdateReq) (categoryDto.CategoryUpdateRes, error)
	ActivateFn   func(ctx context.Context, request categoryDto.CategoryActivateReq) (categoryDto.CategoryActivateRes, error)
	DeactivateFn func(ctx context.Context, request categoryDto.CategoryDeactivateReq) (categoryDto.CategoryDeactivateRes, error)
	DeleteFn     func(ctx context.Context, request categoryDto.CategoryDeleteReq) (categoryDto.CategoryDeleteRes, error)

	CreateCalls     []categoryDto.CategoryCreateReq
	SearchCalls     []categoryDto.CategorySearchReq
	DetailCalls     []categoryDto.CategoryDetailReq
	UpdateCalls     []categoryDto.CategoryUpdateReq
	ActivateCalls   []categoryDto.CategoryActivateReq
	DeactivateCalls []categoryDto.CategoryDeactivateReq
	DeleteCalls     []categoryDto.CategoryDeleteReq
}

func (m *CategoryService) Create(ctx context.Context, request categoryDto.CategoryCreateReq) (categoryDto.CategoryCreateRes, error) {
	m.CreateCalls = append(m.CreateCalls, request)
	if m.CreateFn == nil {
		return categoryDto.CategoryCreateRes{}, nil
	}

	return m.CreateFn(ctx, request)
}

func (m *CategoryService) Search(ctx context.Context, request categoryDto.CategorySearchReq) (categoryDto.CategorySearchRes, error) {
	m.SearchCalls = append(m.SearchCalls, request)
	if m.SearchFn == nil {
		return categoryDto.CategorySearchRes{}, nil
	}

	return m.SearchFn(ctx, request)
}

func (m *CategoryService) Detail(ctx context.Context, request categoryDto.CategoryDetailReq) (categoryDto.CategoryDetailRes, error) {
	m.DetailCalls = append(m.DetailCalls, request)
	if m.DetailFn == nil {
		return categoryDto.CategoryDetailRes{}, nil
	}

	return m.DetailFn(ctx, request)
}

func (m *CategoryService) Update(ctx context.Context, request categoryDto.CategoryUpdateReq) (categoryDto.CategoryUpdateRes, error) {
	m.UpdateCalls = append(m.UpdateCalls, request)
	if m.UpdateFn == nil {
		return categoryDto.CategoryUpdateRes{}, nil
	}

	return m.UpdateFn(ctx, request)
}

func (m *CategoryService) Activate(ctx context.Context, request categoryDto.CategoryActivateReq) (categoryDto.CategoryActivateRes, error) {
	m.ActivateCalls = append(m.ActivateCalls, request)
	if m.ActivateFn == nil {
		return categoryDto.CategoryActivateRes{}, nil
	}

	return m.ActivateFn(ctx, request)
}

func (m *CategoryService) Deactivate(ctx context.Context, request categoryDto.CategoryDeactivateReq) (categoryDto.CategoryDeactivateRes, error) {
	m.DeactivateCalls = append(m.DeactivateCalls, request)
	if m.DeactivateFn == nil {
		return categoryDto.CategoryDeactivateRes{}, nil
	}

	return m.DeactivateFn(ctx, request)
}

func (m *CategoryService) Delete(ctx context.Context, request categoryDto.CategoryDeleteReq) (categoryDto.CategoryDeleteRes, error) {
	m.DeleteCalls = append(m.DeleteCalls, request)
	if m.DeleteFn == nil {
		return categoryDto.CategoryDeleteRes{}, nil
	}

	return m.DeleteFn(ctx, request)
}

// SupplierService is a double of the supplier service.
type SupplierService struct {
	CreateFn     func(ctx context.Context, request supplierDto.SupplierCreateReq) (supplierDto.SupplierCreateRes, error)
	SearchFn     func(ctx context.Context, request supplierDto.SupplierSearchReq) (supplierDto.SupplierSearchRes, error)
	DetailFn     func(ctx context.Context, request supplierDto.SupplierDetailReq) (supplierDto.SupplierDetailRes, error)
	UpdateFn     func(ctx context.Context, request supplierDto.SupplierUpdateReq) (supplierDto.SupplierUpdateRes, error)
	ActivateFn   func(ctx context.Context, request supplierDto.SupplierActivateReq) (supplierDto.SupplierActivateRes, error)
	DeactivateFn func(ctx context.Context, request supplierDto.SupplierDeactivateReq) (supplierDto.SupplierDeactivateRes, error)
	DeleteFn     func(ctx context.Context, request supplierDto.SupplierDeleteReq) (supplierDto.SupplierDeleteRes, error)

	CreateCalls     []supplierDto.SupplierCreateReq
	SearchCalls     []supplierDto.SupplierSearchReq
	DetailCalls     []supplierDto.SupplierDetailReq
	UpdateCalls     []supplierDto.SupplierUpdateReq
	ActivateCalls   []supplierDto.SupplierActivateReq
	DeactivateCalls []supplierDto.SupplierDeactivateReq
	DeleteCalls     []supplierDto.SupplierDeleteReq
}

func (m *SupplierService) Create(ctx context.Context, request supplierDto.SupplierCreateReq) (supplierDto.SupplierCreateRes, error) {
	m.CreateCalls = append(m.CreateCalls, request)
	if m.CreateFn == nil {
		return supplierDto.SupplierCreateRes{}, nil
	}

	return m.CreateFn(ctx, request)
}

func (m *SupplierService) Search(ctx context.Context, request supplierDto.SupplierSearchReq) (supplierDto.SupplierSearchRes, error) {
	m.SearchCalls = append(m.SearchCalls, request)
	if m.SearchFn == nil {
		return supplierDto.SupplierSearchRes{}, nil
	}

	return m.SearchFn(ctx, request)
}

func (m *SupplierService) Detail(ctx context.Context, request supplierDto.SupplierDetailReq) (supplierDto.SupplierDetailRes, error) {
	m.DetailCalls = append(m.DetailCalls, request)
	if m.DetailFn == nil {
		return supplierDto.SupplierDetailRes{}, nil
	}

	return m.DetailFn(ctx, request)
}

func (m *SupplierService) Update(ctx context.Context, request supplierDto.SupplierUpdateReq) (supplierDto.SupplierUpdateRes, error) {
	m.UpdateCalls = append(m.UpdateCalls, request)
	if m.UpdateFn == nil {
		return supplierDto.SupplierUpdateRes{}, nil
	}

	return m.UpdateFn(ctx, request)
}

func (m *SupplierService) Activate(ctx context.Context, request supplierDto.SupplierActivateReq) (supplierDto.SupplierActivateRes, error) {
	m.ActivateCalls = append(m.ActivateCalls, request)
	if m.ActivateFn == nil {
		return supplierDto.SupplierActivateRes{}, nil
	}

	return m.ActivateFn(ctx, request)
}

func (m *SupplierService) Deactivate(ctx context.Context, request supplierDto.SupplierDeactivateReq) (supplierDto.SupplierDeactivateRes, error) {
	m.DeactivateCalls = append(m.DeactivateCalls, request)
	if m.DeactivateFn == nil {
		return supplierDto.SupplierDeactivateRes{}, nil
	}

	return m.DeactivateFn(ctx, request)
}

func (m *SupplierService) Delete(ctx context.Context, request supplierDto.SupplierDeleteReq) (supplierDto.SupplierDeleteRes, error) {
	m.DeleteCalls = append(m.DeleteCalls, request)
	if m.DeleteFn == nil {
		return supplierDto.SupplierDeleteRes{}, nil
	}

	return m.DeleteFn(ctx, request)
}

// ProductService is a double of the product service.
type ProductService struct {
	CreateFn     func(ctx context.Context, request productDto.ProductCreateReq) (productDto.ProductCreateRes, error)
	SearchFn     func(ctx context.Context, request productDto.ProductSearchReq) (productDto.ProductSearchRes, error)
	DetailFn     func(ctx context.Context, request productDto.ProductDetailReq) (productDto.ProductDetailRes, error)
	UpdateFn     func(ctx context.Context, request productDto.ProductUpdateReq) (productDto.ProductUpdateRes, error)
	ActivateFn   func(ctx context.Context, request productDto.ProductActivateReq) (productDto.ProductActivateRes, error)
	DeactivateFn func(ctx context.Context, request productDto.ProductDeactivateReq) (productDto.ProductDeactivateRes, error)
	DeleteFn     func(ctx context.Context, request productDto.ProductDeleteReq) (productDto.ProductDeleteRes, error)

	CreateCalls     []productDto.ProductCreateReq
	SearchCalls     []productDto.ProductSearchReq
	DetailCalls     []productDto.ProductDetailReq
	UpdateCalls     []productDto.ProductUpdateReq
	ActivateCalls   []productDto.ProductActivateReq
	DeactivateCalls []productDto.ProductDeactivateReq
	DeleteCalls     []productDto.ProductDeleteReq
}

func (m *ProductService) Create(ctx context.Context, request productDto.ProductCreateReq) (productDto.ProductCreateRes, error) {
	m.CreateCalls = append(m.CreateCalls, request)
	if m.CreateFn == nil {
		return productDto.ProductCreateRes{}, nil
	}

	return m.CreateFn(ctx, request)
}

func (m *ProductService) Search(ctx context.Context, request productDto.ProductSearchReq) (productDto.ProductSearchRes, error) {
	m.SearchCalls = append(m.SearchCalls, request)
	if m.SearchFn == nil {
		return productDto.ProductSearchRes{}, nil
	}

	return m.SearchFn(ctx, request)
}

func (m *ProductService) Detail(ctx context.Context, request productDto.ProductDetailReq) (productDto.ProductDetailRes, error) {
	m.DetailCalls = append(m.DetailCalls, request)
	if m.DetailFn == nil {
		return productDto.ProductDetailRes{}, nil
	}

	return m.DetailFn(ctx, request)
}

func (m *ProductService) Update(ctx context.Context, request productDto.ProductUpdateReq) (productDto.ProductUpdateRes, error) {
	m.UpdateCalls = append(m.UpdateCalls, request)
	if m.UpdateFn == nil {
		return productDto.ProductUpdateRes{}, nil
	}

	return m.UpdateFn(ctx, request)
}

func (m *ProductService) Activate(ctx context.Context, request productDto.ProductActivateReq) (productDto.ProductActivateRes, error) {
	m.ActivateCalls = append(m.ActivateCalls, request)
	if m.ActivateFn == nil {
		return productDto.ProductActivateRes{}, nil
	}

	return m.ActivateFn(ctx, request)
}

func (m *ProductService) Deactivate(ctx context.Context, request productDto.ProductDeactivateReq) (productDto.ProductDeactivateRes, error) {
	m.DeactivateCalls = append(m.DeactivateCalls, request)
	if m.DeactivateFn == nil {
		return productDto.ProductDeactivateRes{}, nil
	}

	return m.DeactivateFn(ctx, request)
}

func (m *ProductService) Delete(ctx context.Context, request productDto.ProductDeleteReq) (productDto.ProductDeleteRes, error) {
	m.DeleteCalls = append(m.DeleteCalls, request)
	if m.DeleteFn == nil {
		return productDto.ProductDeleteRes{}, nil
	}

	return m.DeleteFn(ctx, request)
}
