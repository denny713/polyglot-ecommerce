package supplier

import (
	"context"
	"errors"
	"net/mail"
	"product-service/internal/model"
	"time"
)

type (
	SupplierCreateReq struct {
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

	SupplierCreateRes struct {
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

// ToObjectModel mapping the request object to table model.Supplier.
func (s SupplierCreateReq) ToObjectModel(ctx context.Context) model.Supplier {
	return model.Supplier{
		Name:          s.Name,
		Phone:         s.Phone,
		Email:         s.Email,
		ContactPerson: s.ContactPerson,
		Address:       s.Address,
		Province:      s.Province,
		City:          s.City,
		District:      s.District,
		Subdistrict:   s.Subdistrict,
		PostalCode:    s.PostalCode,
		Note:          s.Note,
		Base:          model.PrePersist(ctx),
	}
}

// Validate checks the required fields for creating a new supplier.
func (s SupplierCreateReq) Validate() error {
	if s.Name == "" {
		return errors.New("name is required")
	}

	return validateProfile(s.Name, s.Phone, s.Email, s.ContactPerson,
		s.Province, s.City, s.District, s.Subdistrict, s.PostalCode)
}

// ToResponse mapping the table model.Supplier to the response object.
func ToResponse(supplier model.Supplier) SupplierCreateRes {
	return SupplierCreateRes{
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

// validateProfile checks the fields shared by the create and update payloads
// against the column sizes declared on table model.Supplier.
func validateProfile(name, phone, email, contactPerson, province, city, district, subdistrict, postalCode string) error {
	if len(name) > 50 {
		return errors.New("name must not exceed 50 characters")
	}

	if len(phone) > 50 {
		return errors.New("phone must not exceed 50 characters")
	}

	// The supplier table declares email as VARCHAR(50), the tighter of the two
	// bounds is enforced so the insert never fails on the column size.
	if len(email) > 50 {
		return errors.New("email must not exceed 50 characters")
	}

	if email != "" {
		if _, err := mail.ParseAddress(email); err != nil {
			return errors.New("email must be a valid email address")
		}
	}

	if len(contactPerson) > 50 {
		return errors.New("contact_person must not exceed 50 characters")
	}

	for _, area := range []struct {
		field string
		value string
	}{
		{"province", province},
		{"city", city},
		{"district", district},
		{"subdistrict", subdistrict},
	} {
		if len(area.value) > 100 {
			return errors.New(area.field + " must not exceed 100 characters")
		}
	}

	if len(postalCode) > 7 {
		return errors.New("postal_code must not exceed 7 characters")
	}

	return nil
}
