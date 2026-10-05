// Package mocks holds the hand written doubles of the interfaces the layers
// depend on. A field left nil answers with the zero value, so a test only fills
// in the calls it actually cares about.
package mocks

import (
	"context"
	"mime/multipart"

	"product-service/internal/constant"
	categoryDto "product-service/internal/dto/category"
	productDto "product-service/internal/dto/product"
	supplierDto "product-service/internal/dto/supplier"
	"product-service/internal/model"
	"product-service/internal/token"

	"github.com/google/uuid"
	"gorm.io/gorm"
)

// DetailCall is the lookup a repository Detail was asked for. WithStockHistory
// only carries a meaning for the product repository, the others leave it false.
type DetailCall struct {
	Param            string
	Value            interface{}
	WithStockHistory bool
}

// CategoryRepository is a double of the category repository.
type CategoryRepository struct {
	CreateFn func(orm *gorm.DB, category model.Category) (model.Category, error)
	DetailFn func(orm *gorm.DB, param string, value interface{}) (model.Category, error)
	SearchFn func(orm *gorm.DB, filter categoryDto.CategorySearchFilter) ([]model.Category, error)
	UpdateFn func(orm *gorm.DB, category model.Category) (model.Category, error)

	CreateCalls []model.Category
	DetailCalls []DetailCall
	SearchCalls []categoryDto.CategorySearchFilter
	UpdateCalls []model.Category
}

func (m *CategoryRepository) Create(orm *gorm.DB, category model.Category) (model.Category, error) {
	m.CreateCalls = append(m.CreateCalls, category)
	if m.CreateFn == nil {
		return category, nil
	}

	return m.CreateFn(orm, category)
}

func (m *CategoryRepository) Detail(orm *gorm.DB, param string, value interface{}) (model.Category, error) {
	m.DetailCalls = append(m.DetailCalls, DetailCall{Param: param, Value: value})
	if m.DetailFn == nil {
		return model.Category{}, nil
	}

	return m.DetailFn(orm, param, value)
}

func (m *CategoryRepository) Search(orm *gorm.DB, filter categoryDto.CategorySearchFilter) ([]model.Category, error) {
	m.SearchCalls = append(m.SearchCalls, filter)
	if m.SearchFn == nil {
		return nil, nil
	}

	return m.SearchFn(orm, filter)
}

func (m *CategoryRepository) Update(orm *gorm.DB, category model.Category) (model.Category, error) {
	m.UpdateCalls = append(m.UpdateCalls, category)
	if m.UpdateFn == nil {
		return category, nil
	}

	return m.UpdateFn(orm, category)
}

// SupplierRepository is a double of the supplier repository.
type SupplierRepository struct {
	CreateFn func(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error)
	DetailFn func(orm *gorm.DB, param string, value interface{}) (model.Supplier, error)
	SearchFn func(orm *gorm.DB, filter supplierDto.SupplierSearchFilter) ([]model.Supplier, error)
	UpdateFn func(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error)

	CreateCalls []model.Supplier
	DetailCalls []DetailCall
	SearchCalls []supplierDto.SupplierSearchFilter
	UpdateCalls []model.Supplier
}

func (m *SupplierRepository) Create(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error) {
	m.CreateCalls = append(m.CreateCalls, supplier)
	if m.CreateFn == nil {
		return supplier, nil
	}

	return m.CreateFn(orm, supplier)
}

func (m *SupplierRepository) Detail(orm *gorm.DB, param string, value interface{}) (model.Supplier, error) {
	m.DetailCalls = append(m.DetailCalls, DetailCall{Param: param, Value: value})
	if m.DetailFn == nil {
		return model.Supplier{}, nil
	}

	return m.DetailFn(orm, param, value)
}

func (m *SupplierRepository) Search(orm *gorm.DB, filter supplierDto.SupplierSearchFilter) ([]model.Supplier, error) {
	m.SearchCalls = append(m.SearchCalls, filter)
	if m.SearchFn == nil {
		return nil, nil
	}

	return m.SearchFn(orm, filter)
}

func (m *SupplierRepository) Update(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error) {
	m.UpdateCalls = append(m.UpdateCalls, supplier)
	if m.UpdateFn == nil {
		return supplier, nil
	}

	return m.UpdateFn(orm, supplier)
}

// ProductRepository is a double of the product repository.
type ProductRepository struct {
	CreateFn func(orm *gorm.DB, product model.Product) (model.Product, error)
	DetailFn func(orm *gorm.DB, param string, value interface{}, withStockHistory bool) (model.Product, error)
	SearchFn func(orm *gorm.DB, filter productDto.ProductSearchFilter) ([]model.Product, error)
	UpdateFn func(orm *gorm.DB, product model.Product) (model.Product, error)

	CreateCalls []model.Product
	DetailCalls []DetailCall
	SearchCalls []productDto.ProductSearchFilter
	UpdateCalls []model.Product
}

func (m *ProductRepository) Create(orm *gorm.DB, product model.Product) (model.Product, error) {
	m.CreateCalls = append(m.CreateCalls, product)
	if m.CreateFn == nil {
		return product, nil
	}

	return m.CreateFn(orm, product)
}

func (m *ProductRepository) Detail(orm *gorm.DB, param string, value interface{}, withStockHistory bool) (model.Product, error) {
	m.DetailCalls = append(m.DetailCalls, DetailCall{Param: param, Value: value, WithStockHistory: withStockHistory})
	if m.DetailFn == nil {
		return model.Product{}, nil
	}

	return m.DetailFn(orm, param, value, withStockHistory)
}

func (m *ProductRepository) Search(orm *gorm.DB, filter productDto.ProductSearchFilter) ([]model.Product, error) {
	m.SearchCalls = append(m.SearchCalls, filter)
	if m.SearchFn == nil {
		return nil, nil
	}

	return m.SearchFn(orm, filter)
}

func (m *ProductRepository) Update(orm *gorm.DB, product model.Product) (model.Product, error) {
	m.UpdateCalls = append(m.UpdateCalls, product)
	if m.UpdateFn == nil {
		return product, nil
	}

	return m.UpdateFn(orm, product)
}

// StockRepository is a double of the stock position repository.
type StockRepository struct {
	CreateFn func(orm *gorm.DB, position model.StockPosition) (model.StockPosition, error)

	CreateCalls []model.StockPosition
}

func (m *StockRepository) Create(orm *gorm.DB, position model.StockPosition) (model.StockPosition, error) {
	m.CreateCalls = append(m.CreateCalls, position)
	if m.CreateFn == nil {
		return position, nil
	}

	return m.CreateFn(orm, position)
}

// Storage is a double of the object storage repository.
type Storage struct {
	UploadFn    func(ctx context.Context, folder string, file *multipart.FileHeader) (string, error)
	RemoveFn    func(ctx context.Context, objectName string) error
	GetFn       func(fileURL string) string
	ObjectURLFn func(objectName string) string

	Uploaded []string
	Removed  []string
}

func (m *Storage) Upload(ctx context.Context, folder string, file *multipart.FileHeader) (string, error) {
	if m.UploadFn == nil {
		return "", nil
	}

	objectName, err := m.UploadFn(ctx, folder, file)
	if err == nil {
		m.Uploaded = append(m.Uploaded, objectName)
	}

	return objectName, err
}

func (m *Storage) Remove(ctx context.Context, objectName string) error {
	m.Removed = append(m.Removed, objectName)
	if m.RemoveFn == nil {
		return nil
	}

	return m.RemoveFn(ctx, objectName)
}

func (m *Storage) Get(fileURL string) string {
	if m.GetFn == nil {
		return fileURL
	}

	return m.GetFn(fileURL)
}

func (m *Storage) ObjectURL(objectName string) string {
	if m.ObjectURLFn == nil {
		return "http://storage.test/bucket/" + objectName
	}

	return m.ObjectURLFn(objectName)
}

// TokenVerifier is a double of the access token verifier. A nil VerifyFn
// accepts every token as an administrator, so a test about routing does not have
// to mint one.
type TokenVerifier struct {
	VerifyFn func(raw string) (token.Claims, error)

	VerifyCalls []string
}

// DefaultSubject is the account the nil VerifyFn reports.
var DefaultSubject = uuid.MustParse("11111111-2222-3333-4444-555555555555")

func (m *TokenVerifier) Verify(raw string) (token.Claims, error) {
	m.VerifyCalls = append(m.VerifyCalls, raw)
	if m.VerifyFn == nil {
		return token.Claims{Subject: DefaultSubject, Roles: []string{constant.AdminRole}}, nil
	}

	return m.VerifyFn(raw)
}

// Database is a double of the persistence handle the services take. The gorm
// handle it hands out is nil, the repositories behind it are doubles that never
// touch it.
type Database struct {
	OrmFn         func(ctx context.Context) *gorm.DB
	TransactionFn func(ctx context.Context, fn func(tx *gorm.DB) error) error

	Transactions int
}

func (m *Database) Orm(ctx context.Context) *gorm.DB {
	if m.OrmFn == nil {
		return nil
	}

	return m.OrmFn(ctx)
}

func (m *Database) Transaction(ctx context.Context, fn func(tx *gorm.DB) error) error {
	m.Transactions++
	if m.TransactionFn == nil {
		return fn(nil)
	}

	return m.TransactionFn(ctx, fn)
}
