package product

import (
	"product-service/internal/model"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// Update implement repository for update product
func (r repository) Update(orm *gorm.DB, product model.Product) (model.Product, error) {
	var err error

	updates := map[string]interface{}{
		"Name":        product.Name,
		"Description": product.Description,
		"Price":       product.Price,
		"ImageURL":    product.ImageURL,
		"CategoryId":  product.CategoryId,
		"SupplierId":  product.SupplierId,
		"IsActive":    product.IsActive,
		"IsDeleted":   product.IsDeleted,
		"UpdatedBy":   product.UpdatedBy,
		"UpdatedAt":   product.UpdatedAt,
	}

	// The model carries the relations that Detail preloaded. They are omitted
	// so gorm writes only the columns in the map, a preloaded relation would
	// otherwise be saved alongside and assign its foreign key a second time.
	err = orm.Model(&product).
		Omit(clause.Associations).
		Where("is_deleted = FALSE").
		Where("id = ?", product.Id).
		Updates(updates).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
