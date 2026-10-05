package supplier

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	SupplierActivateReq struct {
		Id int64
	}

	SupplierActivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToSupplierActivateRes converts a Supplier model to a SupplierActivateRes DTO.
func ToSupplierActivateRes(supplier model.Supplier) SupplierActivateRes {
	return SupplierActivateRes{
		Id:     supplier.Id,
		Name:   supplier.Name,
		Status: constant.Active,
	}
}
