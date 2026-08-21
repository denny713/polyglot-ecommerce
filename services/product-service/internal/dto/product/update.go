package product

import (
	"errors"
	"mime/multipart"
	"path/filepath"
	"product-service/internal/model"
	"strings"
	"time"

	"github.com/shopspring/decimal"
)

type (
	ProductUpdateReq struct {
		Id          int64
		Name        string
		Description string
		Price       decimal.Decimal
		Image       *multipart.FileHeader
	}

	ProductUpdateRes struct {
		Id          int64           `json:"id"`
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

// ToProductModel mapping the request object to table model.Product.
func (p ProductUpdateReq) ToProductModel(product model.Product) model.Product {
	return model.Product{
		Id:          p.Id,
		Name:        p.Name,
		Description: p.Description,
		Price:       p.Price,
		Base: model.Base{
			IsActive:  product.IsActive,
			IsDeleted: product.IsDeleted,
			CreatedBy: product.CreatedBy,
			UpdatedBy: product.UpdatedBy,
			CreatedAt: product.CreatedAt,
			UpdatedAt: time.Now(),
		},
	}
}

// Validate checks the required fields for creating a new product.
func (p ProductUpdateReq) Validate() error {
	if p.Name == "" {
		return errors.New("name is required")
	}

	if p.Price.IsZero() || p.Price.LessThanOrEqual(decimal.Zero) {
		return errors.New("price is required")
	}

	return nil
}

// ValidateImage checks the uploaded image before it is sent to the object storage.
func (p ProductUpdateReq) ValidateImage() error {
	if p.Image == nil {
		return nil
	}

	if p.Image.Size > maxImageSize {
		return errors.New("image size must not exceed 5 MB")
	}

	ext := strings.ToLower(filepath.Ext(p.Image.Filename))
	if !allowedImageExt[ext] {
		return errors.New("image format must be one of jpg, jpeg, png, or webp")
	}

	return nil
}

// ToProductUpdateRes mapping the table model.Product to the response object.
func ToProductUpdateRes(product model.Product) ProductUpdateRes {
	return ProductUpdateRes{
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
}
