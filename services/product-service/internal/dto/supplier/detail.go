package supplier

import (
	"product-service/internal/model"
	"time"
)

type (
	SupplierDetailReq struct {
		Id int64
	}

	SupplierDetailRes struct {
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
		IsDeleted     bool      `json:"is_deleted"`
		CreatedAt     time.Time `json:"created_at"`
		UpdatedAt     time.Time `json:"updated_at"`
	}
)

// ToSupplierDetailRes mapping the table model.Supplier to the response object.
func ToSupplierDetailRes(supplier model.Supplier) SupplierDetailRes {
	return SupplierDetailRes{
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
		IsDeleted:     supplier.IsDeleted,
		CreatedAt:     supplier.CreatedAt,
		UpdatedAt:     supplier.UpdatedAt,
	}
}
