package category

import (
	"product-service/internal/model"
	"time"
)

type (
	CategoryDetailReq struct {
		Id int64
	}

	CategoryDetailRes struct {
		Id          int64     `json:"id"`
		Name        string    `json:"name"`
		Description string    `json:"description"`
		IsActive    bool      `json:"is_active"`
		IsDeleted   bool      `json:"is_deleted"`
		CreatedAt   time.Time `json:"created_at"`
		UpdatedAt   time.Time `json:"updated_at"`
	}
)

// ToCategoryDetailRes mapping the table model.Category to the response object.
func ToCategoryDetailRes(category model.Category) CategoryDetailRes {
	return CategoryDetailRes{
		Id:          category.Id,
		Name:        category.Name,
		Description: category.Description,
		IsActive:    category.IsActive,
		IsDeleted:   category.IsDeleted,
		CreatedAt:   category.CreatedAt,
		UpdatedAt:   category.UpdatedAt,
	}
}
