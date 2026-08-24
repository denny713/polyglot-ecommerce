package supplier

import (
	"errors"
	"product-service/internal/constant"
	"product-service/internal/dto/base"
	"product-service/internal/model"
	"strings"
)

type (
	SupplierSearchReq struct {
		Name          string
		Phone         string
		Email         string
		ContactPerson string
		Province      string
		City          string
		District      string
		Subdistrict   string
		PostalCode    string
		base.Paging
	}

	SupplierSearchRes struct {
		Data []SupplierDetailRes `json:"data"`
	}

	SupplierSearchFilter struct {
		Name          string
		Phone         string
		Email         string
		ContactPerson string
		Province      string
		City          string
		District      string
		Subdistrict   string
		PostalCode    string
		base.Paging
	}
)

// Validate checks the filters make sense before the query is built. Every
// filter is optional, a zero value simply means the filter is not applied.
func (s SupplierSearchReq) Validate() error {
	sortAllowed := allowedSortBy()

	if sortBy := strings.ToLower(strings.TrimSpace(s.SortBy)); sortBy != "" && !sortAllowed[sortBy] {
		return errors.New("sort_by must be one of id, name, email, contact_person, province, city, created_at, or updated_at")
	}

	if sortOrder := strings.ToLower(strings.TrimSpace(s.SortOrder)); sortOrder != "" &&
		sortOrder != constant.SortOrderAsc && sortOrder != constant.SortOrderDesc {
		return errors.New("sort_order must be one of asc or desc")
	}

	if s.Page < 0 || s.PageSize < 0 {
		return errors.New("page and page_size must not be negative")
	}

	return nil
}

// Normalize trims the text filters and fills the sorting and paging defaults so
// the repository always receives a ready to use request.
func (s SupplierSearchReq) Normalize() SupplierSearchReq {
	s.Name = strings.TrimSpace(s.Name)
	s.Phone = strings.TrimSpace(s.Phone)
	s.Email = strings.TrimSpace(s.Email)
	s.ContactPerson = strings.TrimSpace(s.ContactPerson)
	s.Province = strings.TrimSpace(s.Province)
	s.City = strings.TrimSpace(s.City)
	s.District = strings.TrimSpace(s.District)
	s.Subdistrict = strings.TrimSpace(s.Subdistrict)
	s.PostalCode = strings.TrimSpace(s.PostalCode)
	s.Paging = s.Paging.Normalize(allowedSortBy())

	return s
}

// ToSupplierSearchRes mapping the table model.Supplier rows to the response object.
func ToSupplierSearchRes(suppliers []model.Supplier) SupplierSearchRes {
	data := make([]SupplierDetailRes, 0, len(suppliers))
	for _, supplier := range suppliers {
		data = append(data, ToSupplierDetailRes(supplier))
	}

	return SupplierSearchRes{Data: data}
}

// allowedSortBy returns a map of allowed sort fields for the supplier search.
// This is used to validate the sort_by parameter in the request.
func allowedSortBy() map[string]bool {
	return map[string]bool{
		"id":             true,
		"name":           true,
		"email":          true,
		"contact_person": true,
		"province":       true,
		"city":           true,
		"created_at":     true,
		"updated_at":     true,
	}
}
