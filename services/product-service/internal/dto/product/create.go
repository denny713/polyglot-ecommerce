package product

import (
	"context"
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
		BuyPrice    decimal.Decimal
		SellPrice   decimal.Decimal
		Image       *multipart.FileHeader
		CategoryId  *int64
		SupplierId  *int64
	}

	ProductCreateRes struct {
		Id          int64           `json:"id"`
		Name        string          `json:"name"`
		Description string          `json:"description"`
		BuyPrice    decimal.Decimal `json:"buy_price"`
		SellPrice   decimal.Decimal `json:"sell_price"`
		Category    string          `json:"category"`
		Supplier    string          `json:"supplier"`
		ImageUrl    string          `json:"image_url"`
		IsActive    bool            `json:"is_active"`
		CreatedAt   time.Time       `json:"created_at"`
		UpdatedAt   time.Time       `json:"updated_at"`
	}
)

// ToObjectModel mapping the request object to table model.Product.
func (p ProductCreateReq) ToObjectModel(ctx context.Context) model.Product {
	product := model.Product{
		Name:        p.Name,
		Description: p.Description,
		BuyPrice:    p.BuyPrice,
		SellPrice:   p.SellPrice,
		Base:        model.PrePersist(ctx),
	}

	if p.CategoryId != nil {
		product.CategoryId = *p.CategoryId
	}

	if p.SupplierId != nil {
		product.SupplierId = *p.SupplierId
	}

	return product
}

// Validate checks the required fields for creating a new product.
func (p ProductCreateReq) Validate() error {
	if p.Name == "" {
		return errors.New("name is required")
	}

	if p.CategoryId == nil {
		return errors.New("category is required")
	}

	if p.SupplierId == nil {
		return errors.New("supplier is required")
	}

	if p.BuyPrice.IsZero() || p.BuyPrice.LessThanOrEqual(decimal.Zero) {
		return errors.New("buy price is required")
	}

	if p.SellPrice.IsZero() || p.SellPrice.LessThanOrEqual(decimal.Zero) {
		return errors.New("sell price is required")
	}

	if p.BuyPrice.GreaterThanOrEqual(p.SellPrice) {
		return errors.New("buy price must be less than sell price")
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

// ToProductCreateRes mapping the table model.Product to the response object.
func ToProductCreateRes(product model.Product) ProductCreateRes {
	result := ProductCreateRes{
		Id:          product.Id,
		Name:        product.Name,
		Description: product.Description,
		BuyPrice:    product.BuyPrice,
		SellPrice:   product.SellPrice,
		ImageUrl:    product.ImageURL,
		IsActive:    product.IsActive,
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
