package product

import (
	"product-service/internal/dto/product"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Repository is the contract the product service depends on. The gorm handle
// stays a parameter so a call can be made to join an ongoing transaction.
type Repository interface {
	Create(orm *gorm.DB, product model.Product) (model.Product, error)
	Detail(orm *gorm.DB, param string, value interface{}) (model.Product, error)
	Search(orm *gorm.DB, filter product.ProductSearchFilter) ([]model.Product, error)
	Update(orm *gorm.DB, product model.Product) (model.Product, error)
}

type repository struct{}

// NewRepository builds the product repository.
func NewRepository() Repository {
	return repository{}
}
