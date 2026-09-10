package product

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	ProductActivateReq struct {
		Id int64
	}

	ProductActivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToProductActivateRes converts a Product model to a ProductActivateRes DTO.
func ToProductActivateRes(product model.Product) ProductActivateRes {
	return ProductActivateRes{
		Id:     product.Id,
		Name:   product.Name,
		Status: constant.Active,
	}
}
