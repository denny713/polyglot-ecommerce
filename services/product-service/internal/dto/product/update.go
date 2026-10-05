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
	ProductUpdateReq struct {
		Id          int64
		Name        string
		Description string
		BuyPrice    decimal.Decimal
		SellPrice   decimal.Decimal
		Image       *multipart.FileHeader
		CategoryId  *int64
		SupplierId  *int64
	}

	ProductUpdateRes struct {
		Id          int64           `json:"id"`
		Name        string          `json:"name"`
		Description string          `json:"description"`
		BuyPrice    decimal.Decimal `json:"buy_price"`
		SellPrice   decimal.Decimal `json:"sell_price"`
		Stock       int             `json:"stock"`
		ImageUrl    string          `json:"image_url"`
		Category    string          `json:"category"`
		Supplier    string          `json:"supplier"`
		IsActive    bool            `json:"is_active"`
		IsDeleted   bool            `json:"is_deleted"`
		CreatedAt   time.Time       `json:"created_at"`
		UpdatedAt   time.Time       `json:"updated_at"`
	}
)

// ToProductModel maps the request object onto the product loaded from the
// database, so the status flags, the image and the creation trail of the
// existing row are preserved and only the audit fields are stamped again.
func (p ProductUpdateReq) ToProductModel(ctx context.Context, product model.Product) model.Product {
	product.Id = p.Id
	product.Name = p.Name
	product.Description = p.Description
	product.BuyPrice = p.BuyPrice
	product.SellPrice = p.SellPrice
	product.Base = product.Base.Touch(ctx)

	return product
}

// Validate checks the required fields for creating a new product.
func (p ProductUpdateReq) Validate() error {
	if p.Name == "" {
		return errors.New("name is required")
	}

	// Both relations are dereferenced by the service to read the rows they point
	// at, so a request that omits one is rejected here rather than panicking.
	if p.CategoryId == nil {
		return errors.New("category is required")
	}

	if p.SupplierId == nil {
		return errors.New("supplier is required")
	}

	if p.BuyPrice.IsZero() || p.BuyPrice.LessThanOrEqual(decimal.Zero) {
		return errors.New("buying price is required")
	}

	if p.SellPrice.IsZero() || p.SellPrice.LessThanOrEqual(decimal.Zero) {
		return errors.New("selling price is required")
	}

	if p.BuyPrice.GreaterThanOrEqual(p.SellPrice) {
		return errors.New("buy price must be less than sell price")
	}

	return nil
}

// ValidateImage checks the uploaded image before it is sent to the object storage.
func (p ProductUpdateReq) ValidateImage() error {
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

// ToProductUpdateRes mapping the table model.Product to the response object.
func ToProductUpdateRes(product model.Product) ProductUpdateRes {
	result := ProductUpdateRes{
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
