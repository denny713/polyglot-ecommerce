package stock

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Repository is the contract the product service depends on. The gorm handle
// stays a parameter so a call can be made to join an ongoing transaction.
type Repository interface {
	Create(orm *gorm.DB, position model.StockPosition) (model.StockPosition, error)
}

type repository struct{}

// NewRepository builds the product repository.
func NewRepository() Repository {
	return repository{}
}
