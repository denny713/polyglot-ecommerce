package product

import (
	"context"

	"product-service/internal/constant"
	dto "product-service/internal/dto/product"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// Create implement service for create new product
func (s service) Create(ctx context.Context, request dto.ProductCreateReq) (dto.ProductCreateRes, error) {
	var (
		err        error
		objectName string
		product    model.Product
		category   model.Category
		supplier   model.Supplier

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

	// Get existing category Data
	category, err = s.categories.Detail(orm, "id", *request.CategoryId)
	if err != nil {
		return dto.ProductCreateRes{}, err
	}

	// Get existing supplier data
	supplier, err = s.suppliers.Detail(orm, "id", *request.SupplierId)
	if err != nil {
		return dto.ProductCreateRes{}, err
	}

	newProduct := request.ToObjectModel()

	// The image is uploaded before the row is written, so the image_url stored on
	// the product is the object that really ended up in the bucket.
	if request.Image != nil {
		objectName, err = s.storage.Upload(ctx, constant.ImageFolder, request.Image)
		if err != nil {
			return dto.ProductCreateRes{}, err
		}

		newProduct.ImageURL = s.storage.ObjectURL(objectName)
	}

	// Submit new product
	err = s.db.Transaction(ctx, func(tx *gorm.DB) error {
		product, err = s.products.Create(tx, newProduct)

		return err
	})
	if err != nil {
		s.removeUploadedImage(ctx, objectName)

		return dto.ProductCreateRes{}, err
	}

	product.Category = &category
	product.Supplier = &supplier

	return dto.ToProductCreateRes(product), nil
}
