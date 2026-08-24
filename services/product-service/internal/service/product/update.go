package product

import (
	"context"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	model "product-service/internal/model"
	productRepo "product-service/internal/repository/product"
	storageRepo "product-service/internal/repository/storage"
)

// Update implement service for update product
func Update(ctx context.Context, request dto.ProductUpdateReq) (dto.ProductUpdateRes, error) {
	var (
		err        error
		product    model.Product
		objectName string
		imageUrl   string

		orm = configuration.Orm(ctx)
	)

	// Begin transaction
	tx := orm.Begin()
	defer func() {
		if r := recover(); r != nil {
			tx.Rollback()
		}
	}()

	newProduct := request.ToProductModel(product)
	newProduct.ImageURL = imageUrl

	// Submit new product
	product, err = productRepo.Update(tx, newProduct)
	if err != nil {
		return dto.ProductUpdateRes{}, err
	}

	imageName := storageRepo.Get(product.ImageURL)

	// Image upload for product to storage. Re-Upload image if new image is provided
	if request.Image != nil {
		if imageName != "" {
			removeUploadedImage(ctx, imageName)
		}

		objectName, err = storageRepo.Upload(ctx, dto.ImageFolder, request.Image)
		if err != nil {
			return dto.ProductUpdateRes{}, err
		}

		imageUrl = configuration.MinioObjectURL(objectName)
	}

	// Commit transaction
	if err = tx.Commit().Error; err != nil {
		removeUploadedImage(ctx, objectName)
		return dto.ProductUpdateRes{}, err
	}

	return dto.ToProductUpdateRes(product), nil
}
