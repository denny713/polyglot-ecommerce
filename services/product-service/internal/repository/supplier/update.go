package supplier

import (
	"product-service/internal/model"

	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

// Update implement repository for update supplier
func Update(orm *gorm.DB, supplier model.Supplier) (model.Supplier, error) {
	var err error

	updates := map[string]interface{}{
		"Name":          supplier.Name,
		"Phone":         supplier.Phone,
		"Email":         supplier.Email,
		"ContactPerson": supplier.ContactPerson,
		"Address":       supplier.Address,
		"Province":      supplier.Province,
		"City":          supplier.City,
		"District":      supplier.District,
		"Subdistrict":   supplier.Subdistrict,
		"PostalCode":    supplier.PostalCode,
		"Note":          supplier.Note,
		"IsActive":      supplier.IsActive,
		"IsDeleted":     supplier.IsDeleted,
		"UpdatedBy":     supplier.UpdatedBy,
		"UpdatedAt":     supplier.UpdatedAt,
	}

	// The model carries the relations that Detail preloaded. They are omitted
	// so gorm writes only the columns in the map, a preloaded relation would
	// otherwise be saved alongside and assign its foreign key a second time.
	err = orm.Model(&supplier).
		Omit(clause.Associations).
		Where("is_deleted = FALSE").
		Where("id = ?", supplier.Id).
		Updates(updates).Error
	if err != nil {
		return supplier, err
	}

	return supplier, nil
}
