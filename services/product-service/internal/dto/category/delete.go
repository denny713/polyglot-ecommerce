package category

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	CategoryDeleteReq struct {
		Id int64
	}

	CategoryDeleteRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToCategoryDeleteRes converts a Category model to a CategoryDeleteRes DTO.
func ToCategoryDeleteRes(category model.Category) CategoryDeleteRes {
	return CategoryDeleteRes{
		Id:     category.Id,
		Name:   category.Name,
		Status: constant.Delete,
	}
}
