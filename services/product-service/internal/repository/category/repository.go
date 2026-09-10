package category

import (
	"product-service/internal/dto/category"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Repository is the contract the category service depends on. The gorm handle
// stays a parameter so a call can be made to join an ongoing transaction.
type Repository interface {
	Create(orm *gorm.DB, category model.Category) (model.Category, error)
	Detail(orm *gorm.DB, param string, value interface{}) (model.Category, error)
	Search(orm *gorm.DB, filter category.CategorySearchFilter) ([]model.Category, error)
	Update(orm *gorm.DB, category model.Category) (model.Category, error)
}

type repository struct{}

// NewRepository builds the category repository.
func NewRepository() Repository {
	return repository{}
}
