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

const (
	maxImageSize = 5 << 20
	ImageFolder  = "product"
)

var allowedImageExt = map[string]bool{
	".jpg":  true,
	".jpeg": true,
	".png":  true,
	".webp": true,
}

type (
	ProductCreateReq struct {
		Name        string
		Description string
		Price       decimal.Decimal
		Image       *multipart.FileHeader
	}

	ProductCreateRes struct {
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

// ToProductModel mapping the request object to table model.Product.
func (p ProductCreateReq) ToProductModel() model.Product {
	return model.Product{
		Name:        p.Name,
		Description: p.Description,
		Price:       p.Price,
		IsActive:    true,
		IsDeleted:   false,
		CreatedAt:   time.Now(),
		UpdatedAt:   time.Now(),
	}
}

// ToStockModel mapping the request object to table model.Stock.
func (p ProductCreateReq) ToStockModel() model.Stock {
	return model.Stock{
		Quantity:  0,
		CreatedAt: time.Now(),
		UpdatedAt: time.Now(),
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

	if p.Image.Size > maxImageSize {
		return errors.New("image size must not exceed 5 MB")
	}

	ext := strings.ToLower(filepath.Ext(p.Image.Filename))
	if !allowedImageExt[ext] {
		return errors.New("image format must be one of jpg, jpeg, png, or webp")
	}

	return nil
}

// ToProductCreateRes mapping the table model.Product to the response object.
func ToProductCreateRes(product model.Product) ProductCreateRes {
	var quantity int
	if product.Stock != nil {
		quantity = product.Stock.Quantity
	}

	return ProductCreateRes{
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
