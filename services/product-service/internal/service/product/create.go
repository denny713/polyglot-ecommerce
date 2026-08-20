package product

import (
	"context"
	"product-service/internal/configuration"
	dto "product-service/internal/dto/product"
	model "product-service/internal/model"
	productRepo "product-service/internal/repository/product"
	stockRepo "product-service/internal/repository/stock"
	storageRepo "product-service/internal/repository/storage"
)

// Create implement service for create new product
func Create(ctx context.Context, request dto.ProductCreateReq) (dto.ProductCreateRes, error) {
	var (
		err        error
		product    model.Product
		stock      model.Stock
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

	newProduct := request.ToProductModel()
	newProduct.ImageURL = imageUrl

	// Submit new product
	product, err = productRepo.Create(tx, newProduct)
	if err != nil {
		return dto.ProductCreateRes{}, err
	}

	newStock := request.ToStockModel()
	newStock.ProductID = product.ID

	// Submit initial stock for new product
	stock, err = stockRepo.Create(tx, newStock)
	if err != nil {
		return dto.ProductCreateRes{}, err
	}

	product.Stock = &stock

	// Image upload for product to storage
	if request.Image != nil {
		objectName, err = storageRepo.Upload(ctx, dto.ImageFolder, request.Image)
		if err != nil {
			return dto.ProductCreateRes{}, err
		}

		imageUrl = configuration.MinioObjectURL(objectName)
	}

	// Commit transaction
	if err = tx.Commit().Error; err != nil {
		removeUploadedImage(ctx, objectName)
		return dto.ProductCreateRes{}, err
	}

	return dto.ToProductCreateRes(product), nil
}

// removeUploadedImage cleans up the object so a failed create does not leave an
// orphan file in the bucket. The cleanup error is intentionally ignored, the
// original error is the one worth returning to the caller.
func removeUploadedImage(ctx context.Context, objectName string) {
	if objectName == "" {
		return
	}

	_ = storageRepo.Remove(ctx, objectName)
}
