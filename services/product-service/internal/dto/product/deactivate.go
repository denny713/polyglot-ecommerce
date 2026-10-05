package product

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	ProductDeactivateReq struct {
		Id int64
	}

	ProductDeactivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToProductDeactivateRes converts a Product model to a ProductDeactivateRes DTO.
func ToProductDeactivateRes(product model.Product) ProductDeactivateRes {
	return ProductDeactivateRes{
		Id:     product.Id,
		Name:   product.Name,
		Status: constant.Inactive,
	}
}
