package category

import (
	"product-service/internal/constant"
	"product-service/internal/model"
)

type (
	CategoryDeactivateReq struct {
		Id int64
	}

	CategoryDeactivateRes struct {
		Id     int64  `json:"id"`
		Name   string `json:"name"`
		Status string `json:"status"`
	}
)

// ToCategoryDeactivateRes converts a Category model to a CategoryDeactivateRes DTO.
func ToCategoryDeactivateRes(category model.Category) CategoryDeactivateRes {
	return CategoryDeactivateRes{
		Id:     category.Id,
		Name:   category.Name,
		Status: constant.Inactive,
	}
}
