package supplier

import (
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Create implement repository for create new supplier
func (r repository) Create(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error) {
	err := orm.Create(&supplier).Error
	if err != nil {
		return model.Supplier{}, err
	}

	return supplier, nil
}
