package category

import (
	"errors"
	"product-service/internal/model"
	"time"
)

type (
	CategoryUpdateReq struct {
		Id          int64
		Name        string
		Description string
	}

	CategoryUpdateRes struct {
		Id          int64     `json:"id"`
		Name        string    `json:"name"`
		Description string    `json:"description"`
		IsActive    bool      `json:"is_active"`
		CreatedAt   time.Time `json:"created_at"`
		UpdatedAt   time.Time `json:"updated_at"`
	}
)

// ToObjectModel mapping the request object to table model.Category.
func (c CategoryUpdateReq) ToObjectModel() model.Category {
	return model.Category{
		Id:          c.Id,
		Name:        c.Name,
		Description: c.Description,
		Base:        model.PreUpdate(),
	}
}

// Validate checks the required fields for updating an existing category.
func (c CategoryUpdateReq) Validate() error {
	if c.Id == 0 {
		return errors.New("id is required")
	}

	if c.Name == "" {
		return errors.New("name is required")
	}

	return nil
}

// ToCategoryUpdateRes mapping the table model.Category to response object.
func ToCategoryUpdateRes(category model.Category) CategoryUpdateRes {
	return CategoryUpdateRes{
		Id:          category.Id,
		Name:        category.Name,
		Description: category.Description,
		IsActive:    category.IsActive,
		CreatedAt:   category.CreatedAt,
		UpdatedAt:   category.UpdatedAt,
	}
}
