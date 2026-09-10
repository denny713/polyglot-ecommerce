package product

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	ProductDeleteReq struct {
		Id int64
	}

	ProductDeleteRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToProductDeleteRes converts a Product model to a ProductDeleteRes DTO.
func ToProductDeleteRes(product model.Product) ProductDeleteRes {
	return ProductDeleteRes{
		Id:     product.Id,
		Name:   product.Name,
		Status: constant.Delete,
	}
}
