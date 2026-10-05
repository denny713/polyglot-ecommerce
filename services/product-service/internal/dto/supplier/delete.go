package supplier

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	SupplierDeleteReq struct {
		Id int64
	}

	SupplierDeleteRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToSupplierDeleteRes converts a Supplier model to a SupplierDeleteRes DTO.
func ToSupplierDeleteRes(supplier model.Supplier) SupplierDeleteRes {
	return SupplierDeleteRes{
		Id:     supplier.Id,
		Name:   supplier.Name,
		Status: constant.Delete,
	}
}
