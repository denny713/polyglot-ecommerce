package supplier

import (
	"errors"
	"product-service/internal/model"
	"time"
)

type (
	SupplierUpdateReq struct {
		Id            int64  `json:"-"`
		Name          string `json:"name"`
		Phone         string `json:"phone"`
		Email         string `json:"email"`
		ContactPerson string `json:"contact_person"`
		Address       string `json:"address"`
		Province      string `json:"province"`
		City          string `json:"city"`
		District      string `json:"district"`
		Subdistrict   string `json:"subdistrict"`
		PostalCode    string `json:"postal_code"`
		Note          string `json:"note"`
	}

	SupplierUpdateRes struct {
		Id            int64     `json:"id"`
		Name          string    `json:"name"`
		Phone         string    `json:"phone"`
		Email         string    `json:"email"`
		ContactPerson string    `json:"contact_person"`
		Address       string    `json:"address"`
		Province      string    `json:"province"`
		City          string    `json:"city"`
		District      string    `json:"district"`
		Subdistrict   string    `json:"subdistrict"`
		PostalCode    string    `json:"postal_code"`
		Note          string    `json:"note"`
		IsActive      bool      `json:"is_active"`
		CreatedAt     time.Time `json:"created_at"`
		UpdatedAt     time.Time `json:"updated_at"`
	}
)

// ToObjectModel maps the request object onto the supplier loaded from the
// database, so the status flags and the creation trail of the existing row are
// preserved and only the audit fields are stamped again.
func (s SupplierUpdateReq) ToObjectModel(supplier model.Supplier) model.Supplier {
	supplier.Id = s.Id
	supplier.Name = s.Name
	supplier.Phone = s.Phone
	supplier.Email = s.Email
	supplier.ContactPerson = s.ContactPerson
	supplier.Address = s.Address
	supplier.Province = s.Province
	supplier.City = s.City
	supplier.District = s.District
	supplier.Subdistrict = s.Subdistrict
	supplier.PostalCode = s.PostalCode
	supplier.Note = s.Note
	supplier.Base = supplier.Base.Touch()

	return supplier
}

// Validate checks the required fields for updating an existing supplier.
func (s SupplierUpdateReq) Validate() error {
	if s.Id == 0 {
		return errors.New("id is required")
	}

	if s.Name == "" {
		return errors.New("name is required")
	}

	return validateProfile(s.Name, s.Phone, s.Email, s.ContactPerson,
		s.Province, s.City, s.District, s.Subdistrict, s.PostalCode)
}

// ToSupplierUpdateRes mapping the table model.Supplier to response object.
func ToSupplierUpdateRes(supplier model.Supplier) SupplierUpdateRes {
	return SupplierUpdateRes{
		Id:            supplier.Id,
		Name:          supplier.Name,
		Phone:         supplier.Phone,
		Email:         supplier.Email,
		ContactPerson: supplier.ContactPerson,
		Address:       supplier.Address,
		Province:      supplier.Province,
		City:          supplier.City,
		District:      supplier.District,
		Subdistrict:   supplier.Subdistrict,
		PostalCode:    supplier.PostalCode,
		Note:          supplier.Note,
		IsActive:      supplier.IsActive,
		CreatedAt:     supplier.CreatedAt,
		UpdatedAt:     supplier.UpdatedAt,
	}
}
