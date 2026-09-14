package category

import (
	"context"
	"errors"
	"product-service/internal/model"
	"time"
)

type (
	CategoryCreateReq struct {
		Name        string `json:"name"`
		Description string `json:"description"`
	}

	CategoryCreateRes struct {
		Id          int64     `json:"id"`
		Name        string    `json:"name"`
		Description string    `json:"description"`
		IsActive    bool      `json:"is_active"`
		CreatedAt   time.Time `json:"created_at"`
		UpdatedAt   time.Time `json:"updated_at"`
	}
)

// ToObjectModel mapping the request object to table model.Category.
func (c CategoryCreateReq) ToObjectModel(ctx context.Context) model.Category {
	return model.Category{
		Name:        c.Name,
		Description: c.Description,
		Base:        model.PrePersist(ctx),
	}
}

// Validate checks the required fields for creating a new category.
func (c CategoryCreateReq) Validate() error {
	if c.Name == "" {
		return errors.New("name is required")
	}

	return nil
}

// ToResponse mapping the table model.Category to response object.
func ToResponse(category model.Category) CategoryCreateRes {
	return CategoryCreateRes{
		Id:          category.Id,
		Name:        category.Name,
		Description: category.Description,
		IsActive:    category.IsActive,
		CreatedAt:   category.CreatedAt,
		UpdatedAt:   category.UpdatedAt,
	}
}
