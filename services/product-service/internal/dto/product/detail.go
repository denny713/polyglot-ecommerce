package product

import (
	"product-service/internal/model"
	"time"

	"github.com/shopspring/decimal"
)

type (
	ProductDetailReq struct {
		ID int64
	}

	ProductDetailRes struct {
		ID          int64           `json:"id"`
		Name        string          `json:"name"`
		Description string          `json:"description"`
		Price       decimal.Decimal `json:"price"`
		Stock       int             `json:"stock"`
		ImageUrl    string          `json:"image_url"`
		IsActive    bool            `json:"is_active"`
		IsDeleted   bool            `json:"is_deleted"`
		CreatedAt   time.Time       `json:"created_at"`
		UpdatedAt   time.Time       `json:"updated_at"`
	}
)

// ToProductDetailRes mapping the table model.Product to the response object.
func ToProductDetailRes(product model.Product) ProductDetailRes {
	var quantity int
	if product.Stock != nil {
		quantity = product.Stock.Quantity
	}

	return ProductDetailRes{
		ID:          product.ID,
		Name:        product.Name,
		Description: product.Description,
		Price:       product.Price,
		Stock:       quantity,
		ImageUrl:    product.ImageURL,
		IsActive:    product.IsActive,
		IsDeleted:   product.IsDeleted,
		CreatedAt:   product.CreatedAt,
		UpdatedAt:   product.UpdatedAt,
	}
}
