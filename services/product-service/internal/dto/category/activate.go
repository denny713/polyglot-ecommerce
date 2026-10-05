package category

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	CategoryActivateReq struct {
		Id int64
	}

	CategoryActivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToCategoryActivateRes converts a Category model to a CategoryActivateRes DTO.
func ToCategoryActivateRes(category model.Category) CategoryActivateRes {
	return CategoryActivateRes{
		Id:     category.Id,
		Name:   category.Name,
		Status: constant.Active,
	}
}
