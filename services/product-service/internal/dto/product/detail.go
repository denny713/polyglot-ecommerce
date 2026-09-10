package product

import (
	"product-service/internal/model"
	"time"

	"github.com/shopspring/decimal"
)

type (
	ProductDetailReq struct {
		Id int64
	}

	ProductDetailRes struct {
		Id          int64           `json:"id"`
		Name        string          `json:"name"`
		Description string          `json:"description"`
		Price       decimal.Decimal `json:"price"`
		ImageUrl    string          `json:"image_url"`
		Category    string          `json:"category"`
		Supplier    string          `json:"supplier"`
		IsActive    bool            `json:"is_active"`
		IsDeleted   bool            `json:"is_deleted"`
		CreatedAt   time.Time       `json:"created_at"`
		UpdatedAt   time.Time       `json:"updated_at"`
	}
)

// ToProductDetailRes mapping the table model.Product to the response object.
func ToProductDetailRes(product model.Product) ProductDetailRes {
	result := ProductDetailRes{
		Id:          product.Id,
		Name:        product.Name,
		Description: product.Description,
		Price:       product.Price,
		ImageUrl:    product.ImageURL,
		IsActive:    product.IsActive,
		IsDeleted:   product.IsDeleted,
		CreatedAt:   product.CreatedAt,
		UpdatedAt:   product.UpdatedAt,
	}

	if product.Category != nil {
		result.Category = product.Category.Name
	}

	if product.Supplier != nil {
		result.Supplier = product.Supplier.Name
	}

	return result
}
