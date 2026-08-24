package product

import (
	"context"
	"product-service/internal/configuration"
	"product-service/internal/constant"
	dto "product-service/internal/dto/product"
	"product-service/internal/model"
	categoryRepo "product-service/internal/repository/category"
	productRepo "product-service/internal/repository/product"
	storageRepo "product-service/internal/repository/storage"
	supplierRepo "product-service/internal/repository/supplier"
)

// Create implement service for create new product
func Create(ctx context.Context, request dto.ProductCreateReq) (dto.ProductCreateRes, error) {
	var (
		err        error
		objectName string
		product    model.Product
		category   model.Category
		supplier   model.Supplier

		orm = configuration.Orm(ctx)
	)

	// Get existing category Data
	category, err = categoryRepo.Detail(orm, "id", *request.CategoryId)
	if err != nil {
		return dto.ProductCreateRes{}, err
	}

	// Get existing supplier data
	supplier, err = supplierRepo.Detail(orm, "id", *request.SupplierId)
	if err != nil {
		return dto.ProductCreateRes{}, err
	}

	newProduct := request.ToObjectModel()

	// The image is uploaded before the row is written, so the image_url stored on
	// the product is the object that really ended up in the bucket.
	if request.Image != nil {
		objectName, err = storageRepo.Upload(ctx, constant.ImageFolder, request.Image)
		if err != nil {
			return dto.ProductCreateRes{}, err
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

	// Submit new product
	product, err = productRepo.Create(tx, newProduct)
	if err != nil {
		tx.Rollback()
		RemoveUploadedImage(ctx, objectName)

		return dto.ProductCreateRes{}, err
	}

	product.Category = &category
	product.Supplier = &supplier

	// Commit transaction
	if err = tx.Commit().Error; err != nil {
		RemoveUploadedImage(ctx, objectName)

		return dto.ProductCreateRes{}, err
	}

	return dto.ToProductCreateRes(product), nil
}

// RemoveUploadedImage cleans up the object so a failed write does not leave an
// orphan file in the bucket. The cleanup error is intentionally ignored, the
// original error is the one worth returning to the caller.
func RemoveUploadedImage(ctx context.Context, objectName string) {
	if objectName == "" {
		return
	}

	_ = storageRepo.Remove(ctx, objectName)
}
