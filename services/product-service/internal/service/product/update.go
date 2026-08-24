package product

import (
	"context"
	"errors"
	"product-service/internal/configuration"
	"product-service/internal/constant"
	dto "product-service/internal/dto/product"
	"product-service/internal/exception"
	categoryRepo "product-service/internal/repository/category"
	productRepo "product-service/internal/repository/product"
	storageRepo "product-service/internal/repository/storage"
	supplierRepo "product-service/internal/repository/supplier"

	"gorm.io/gorm"
)

// Update implement service for update product
func Update(ctx context.Context, request dto.ProductUpdateReq) (dto.ProductUpdateRes, error) {
	var (
		objectName string

		orm = configuration.Orm(ctx)
	)

	// Get existing product
	existing, err := productRepo.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductUpdateRes{}, exception.ErrNotFound
		}

		return dto.ProductUpdateRes{}, err
	}

	// Get existing category Data
	category, err := categoryRepo.Detail(orm, "id", *request.CategoryId)
	if err != nil {
		return dto.ProductUpdateRes{}, err
	}

	// Get existing supplier data
	supplier, err := supplierRepo.Detail(orm, "id", *request.SupplierId)
	if err != nil {
		return dto.ProductUpdateRes{}, err
	}

	newProduct := request.ToProductModel(existing)
	newProduct.CategoryId = category.Id
	newProduct.SupplierId = supplier.Id

	// The new image is uploaded before the row is written, so image_url never
	// points at an object that is missing from the bucket. A request without an
	// image keeps the one the product already has.
	if request.Image != nil {
		objectName, err = storageRepo.Upload(ctx, constant.ImageFolder, request.Image)
		if err != nil {
			return dto.ProductUpdateRes{}, err
		}

		newProduct.ImageURL = configuration.MinioObjectURL(objectName)
	}

	// Begin transaction
	tx := orm.Begin()
	defer func() {
		if r := recover(); r != nil {
			tx.Rollback()
			RemoveUploadedImage(ctx, objectName)
		}
	}()

	// Submit the product
	product, err := productRepo.Update(tx, newProduct)
	if err != nil {
		tx.Rollback()
		RemoveUploadedImage(ctx, objectName)

		return dto.ProductUpdateRes{}, err
	}

	// Commit transaction
	if err = tx.Commit().Error; err != nil {
		RemoveUploadedImage(ctx, objectName)

		return dto.ProductUpdateRes{}, err
	}

	product.Category = &category
	product.Supplier = &supplier

	// The image being replaced is dropped only once the new row is committed, a
	// failed update must leave the product with a reachable image.
	if objectName != "" {
		RemoveUploadedImage(ctx, storageRepo.Get(existing.ImageURL))
	}

	return dto.ToProductUpdateRes(product), nil
}
