package product

import (
	"context"
	"errors"

	"product-service/internal/constant"
	dto "product-service/internal/dto/product"
	"product-service/internal/exception"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Update implement service for update product
func (s service) Update(ctx context.Context, request dto.ProductUpdateReq) (dto.ProductUpdateRes, error) {
	var (
		objectName string
		product    model.Product

		orm = s.db.Orm(ctx)
	)

	// The image is removed again when the write below panics, so a request that
	// dies half way does not leave an orphan file in the bucket.
	defer func() {
		if r := recover(); r != nil {
			s.removeUploadedImage(ctx, objectName)
			panic(r)
		}
	}()

	// Get existing product
	existing, err := s.products.Detail(orm, "id", request.Id)
	if err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return dto.ProductUpdateRes{}, exception.ErrNotFound
		}

		return dto.ProductUpdateRes{}, err
	}

	// Get existing category Data
	category, err := s.categories.Detail(orm, "id", *request.CategoryId)
	if err != nil {
		return dto.ProductUpdateRes{}, err
	}

	// Get existing supplier data
	supplier, err := s.suppliers.Detail(orm, "id", *request.SupplierId)
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
		objectName, err = s.storage.Upload(ctx, constant.ImageFolder, request.Image)
		if err != nil {
			return dto.ProductUpdateRes{}, err
		}

		newProduct.ImageURL = s.storage.ObjectURL(objectName)
	}

	// Submit the product
	err = s.db.Transaction(ctx, func(tx *gorm.DB) error {
		product, err = s.products.Update(tx, newProduct)

		return err
	})
	if err != nil {
		s.removeUploadedImage(ctx, objectName)

		return dto.ProductUpdateRes{}, err
	}

	product.Category = &category
	product.Supplier = &supplier

	// The image being replaced is dropped only once the new row is committed, a
	// failed update must leave the product with a reachable image.
	if objectName != "" {
		s.removeUploadedImage(ctx, s.storage.Get(existing.ImageURL))
	}

	return dto.ToProductUpdateRes(product), nil
}
