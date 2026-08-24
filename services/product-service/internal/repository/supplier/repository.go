package supplier

import (
	"product-service/internal/dto/supplier"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Repository is the contract the supplier service depends on. The gorm handle
// stays a parameter so a call can be made to join an ongoing transaction.
type Repository interface {
	Create(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error)
	Detail(orm *gorm.DB, param string, value interface{}) (model.Supplier, error)
	Search(orm *gorm.DB, filter supplier.SupplierSearchFilter) ([]model.Supplier, error)
	Update(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error)
}

type repository struct{}

// NewRepository builds the supplier repository.
func NewRepository() Repository {
	return repository{}
}
