package product

import (
	"errors"
	"mime/multipart"
	"path/filepath"
	"product-service/internal/constant"
	"product-service/internal/model"
	"strings"
	"time"

	"github.com/shopspring/decimal"
)

type (
	ProductCreateReq struct {
		Name        string
		Description string
		Price       decimal.Decimal
		Image       *multipart.FileHeader
	}

	ProductCreateRes struct {
		Id          int64           `json:"id"`
		Name        string          `json:"name"`
		Description string          `json:"description"`
		Price       decimal.Decimal `json:"price"`
		ImageUrl    string          `json:"image_url"`
		IsActive    bool            `json:"is_active"`
		CreatedAt   time.Time       `json:"created_at"`
		UpdatedAt   time.Time       `json:"updated_at"`
	}
)

// ToObjectModel mapping the request object to table model.Product.
func (p ProductCreateReq) ToObjectModel() model.Product {
	return model.Product{
		Name:        p.Name,
		Description: p.Description,
		Price:       p.Price,
		Base:        model.PrePersist(),
	}
}

// Validate checks the required fields for creating a new product.
func (p ProductCreateReq) Validate() error {
	if p.Name == "" {
		return errors.New("name is required")
	}

	if p.Price.IsZero() || p.Price.LessThanOrEqual(decimal.Zero) {
		return errors.New("price is required")
	}

	return nil
}

// ValidateImage checks the uploaded image before it is sent to the object storage.
func (p ProductCreateReq) ValidateImage() error {
	if p.Image == nil {
		return nil
	}

	if p.Image.Size > constant.MaxImageSize {
		return errors.New("image size must not exceed 5 MB")
	}

	ext := strings.ToLower(filepath.Ext(p.Image.Filename))
	if !AllowedImageExt()[ext] {
		return errors.New("image format must be one of jpg, jpeg, png, or webp")
	}

	return nil
}

// AllowedImageExt returns a map of allowed image extensions for product images.
func AllowedImageExt() map[string]bool {
	return map[string]bool{
		".jpg":  true,
		".jpeg": true,
		".png":  true,
		".webp": true,
	}
}

// ToResponse mapping the table model.Product to the response object.
func ToResponse(product model.Product) ProductCreateRes {
	return ProductCreateRes{
		Id:          product.Id,
		Name:        product.Name,
		Description: product.Description,
		Price:       product.Price,
		ImageUrl:    product.ImageURL,
		IsActive:    product.IsActive,
		CreatedAt:   product.CreatedAt,
		UpdatedAt:   product.UpdatedAt,
	}
}
