package supplier

import (
	"product-service/internal/dto/base"
	"product-service/internal/dto/supplier"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// sortColumns maps user-facing sort keys to database column names for supplier.
var sortColumns = map[string]string{
	"id":             "supplier.id",
	"name":           "supplier.name",
	"email":          "supplier.email",
	"contact_person": "supplier.contact_person",
	"province":       "supplier.province",
	"city":           "supplier.city",
	"created_at":     "supplier.created_at",
	"updated_at":     "supplier.updated_at",
}

// Search implement repository for search supplier by its profile and address
func Search(orm *gorm.DB, filter supplier.SupplierSearchFilter) ([]model.Supplier, error) {
	suppliers := make([]model.Supplier, 0)

	query := orm.Model(&model.Supplier{}).
		Select("supplier.*").
		Where("supplier.is_deleted = FALSE")

	filters := []struct {
		column string
		value  string
	}{
		{"supplier.name", filter.Name},
		{"supplier.phone", filter.Phone},
		{"supplier.email", filter.Email},
		{"supplier.contact_person", filter.ContactPerson},
		{"supplier.province", filter.Province},
		{"supplier.city", filter.City},
		{"supplier.district", filter.District},
		{"supplier.subdistrict", filter.Subdistrict},
		{"supplier.postal_code", filter.PostalCode},
	}

	for _, f := range filters {
		if f.value != "" {
			query = query.Where(f.column+" ILIKE ?", "%"+f.value+"%")
		}
	}

	if limit := filter.Paging.Limit(); limit > 0 {
		query = query.Limit(limit)
	}

	if offset := filter.Paging.Offset(); offset > 0 {
		query = query.Offset(offset)
	}

	err := query.Order(base.OrderClause(sortColumns, filter.SortBy, filter.SortOrder)).Find(&suppliers).Error
	if err != nil {
		return nil, err
	}

	return suppliers, nil
}
