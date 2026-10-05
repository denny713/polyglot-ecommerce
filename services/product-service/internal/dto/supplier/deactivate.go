package supplier

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	SupplierDeactivateReq struct {
		Id int64
	}

	SupplierDeactivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToSupplierDeactivateRes converts a Supplier model to a SupplierDeactivateRes DTO.
func ToSupplierDeactivateRes(supplier model.Supplier) SupplierDeactivateRes {
	return SupplierDeactivateRes{
		Id:     supplier.Id,
		Name:   supplier.Name,
		Status: constant.Inactive,
	}
}
