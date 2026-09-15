package product

import (
	"context"
	"errors"
	"mime/multipart"
	"net/textproto"
	"testing"
	"time"

	"product-service/internal/account"
	"product-service/internal/constant"
	"product-service/internal/dto/base"
	dto "product-service/internal/dto/product"
	"product-service/internal/exception"
	"product-service/internal/mocks"
	"product-service/internal/model"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

var (
	errDatabase = errors.New("connection reset by peer")
	errStorage  = errors.New("the bucket refused the object")
)

// existingActor is the audit trail a row loaded from the database already
// carries, the writes under test have to leave it on CreatedBy.
var existingActor = uuid.MustParse("2b1f8f4a-0000-4000-8000-00000000002a")

// caller is the account the token middleware puts on the request context, the
// subject of the verified access token. The writes under test have to stamp it
// on the rows they audit.
var caller = uuid.MustParse("9a7c1d2e-0000-4000-8000-00000000009a")

// callerContext is the context a request that went through the authorization
// middleware arrives with.
func callerContext() context.Context {
	return account.WithUserLogin(context.Background(), caller)
}

// repositories gathers the doubles a product service is built on, so a test can
// reach the one it wants to script.
type repositories struct {
	products      *mocks.ProductRepository
	categories    *mocks.CategoryRepository
	suppliers     *mocks.SupplierRepository
	stockPosition *mocks.StockRepository
	storage       *mocks.Storage
	database      *mocks.Database
}

func newServiceWith(t *testing.T) (Service, *repositories) {
	t.Helper()

	deps := &repositories{
		products:      &mocks.ProductRepository{},
		categories:    &mocks.CategoryRepository{},
		suppliers:     &mocks.SupplierRepository{},
		stockPosition: &mocks.StockRepository{},
		storage:       &mocks.Storage{},
		database:      &mocks.Database{},
	}

	// The relations a product points at resolve by default, so a test only
	// scripts the lookup it wants to see fail.
	deps.categories.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{Id: 3, Name: "Elektronik"}, nil
	}
	deps.suppliers.DetailFn = func(*gorm.DB, string, interface{}) (model.Supplier, error) {
		return model.Supplier{Id: 4, Name: "PT Maju"}, nil
	}

	service := NewService(deps.database, deps.products, deps.categories, deps.suppliers,
		deps.stockPosition, deps.storage)

	return service, deps
}

// newService keeps the shape the shared tests below expect, the product
// repository is the only double they script.
func newService(t *testing.T) (Service, *mocks.ProductRepository, *mocks.Database) {
	t.Helper()

	service, deps := newServiceWith(t)

	return service, deps.products, deps.database
}

func existingProduct() model.Product {
	created := time.Date(2024, time.January, 2, 3, 4, 5, 0, time.UTC)

	return model.Product{
		Id:          7,
		Name:        "Kipas Angin",
		Description: "Kipas angin berdiri",
		BuyPrice:    decimal.RequireFromString("150.00"),
		SellPrice:   decimal.RequireFromString("199.99"),
		ImageURL:    "http://storage.test/bucket/product/lama.png",
		CategoryId:  3,
		SupplierId:  4,
		Base: model.Base{
			IsActive:  true,
			CreatedBy: existingActor,
			UpdatedBy: existingActor,
			CreatedAt: created,
			UpdatedAt: created,
		},
	}
}

// productWithStockHistory is what the repository hands back when the stock trail
// was asked for: the relations resolved and the movements preloaded.
func productWithStockHistory() model.Product {
	product := existingProduct()
	product.Category = &model.Category{Id: 3, Name: "Elektronik"}
	product.Supplier = &model.Supplier{Id: 4, Name: "PT Maju"}
	product.StockPosition = &model.StockPosition{Id: 1, ProductId: 7, Quantity: 12}
	product.Stock = &[]model.Stock{{
		Id:             8,
		ProductId:      7,
		DocumentNumber: "PO-2024-0001",
		DocumentType:   "PO",
		Activity:       "IN",
		Quantity:       12,
	}}

	return product
}

func id(value int64) *int64 {
	return &value
}

func uploadedImage() *multipart.FileHeader {
	return &multipart.FileHeader{
		Filename: "kipas.png",
		Header:   textproto.MIMEHeader{"Content-Type": []string{"image/png"}},
		Size:     1024,
	}
}

func createReq(image *multipart.FileHeader) dto.ProductCreateReq {
	return dto.ProductCreateReq{
		Name:        "Kipas Angin",
		Description: "Kipas angin berdiri",
		BuyPrice:    decimal.RequireFromString("150.00"),
		SellPrice:   decimal.RequireFromString("199.99"),
		Image:       image,
		CategoryId:  id(3),
		SupplierId:  id(4),
	}
}

func updateReq(image *multipart.FileHeader) dto.ProductUpdateReq {
	return dto.ProductUpdateReq{
		Id:          7,
		Name:        "Kipas Angin Baru",
		Description: "Deskripsi baru",
		BuyPrice:    decimal.RequireFromString("180.00"),
		SellPrice:   decimal.RequireFromString("249.99"),
		Image:       image,
		CategoryId:  id(3),
		SupplierId:  id(4),
	}
}

func TestCreate(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.CreateFn = func(_ *gorm.DB, product model.Product) (model.Product, error) {
		product.Id = 11

		return product, nil
	}

	got, err := service.Create(callerContext(), createReq(nil))

	require.NoError(t, err)
	require.Equal(t, int64(11), got.Id)
	require.Equal(t, "Kipas Angin", got.Name)
	require.True(t, got.IsActive)

	// The relations are read before the row is written, and their names are what
	// the response reports.
	require.Equal(t, "Elektronik", got.Category)
	require.Equal(t, "PT Maju", got.Supplier)
	require.Equal(t, []mocks.DetailCall{{Param: "id", Value: int64(3)}}, deps.categories.DetailCalls)
	require.Equal(t, []mocks.DetailCall{{Param: "id", Value: int64(4)}}, deps.suppliers.DetailCalls)

	// A request without an image writes no image url and touches no bucket.
	require.Len(t, deps.products.CreateCalls, 1)
	require.Empty(t, deps.products.CreateCalls[0].ImageURL)
	require.Empty(t, deps.storage.Uploaded)
	require.Empty(t, deps.storage.Removed)

	// The row is written inside a transaction.
	require.Equal(t, 1, deps.database.Transactions)
}

func TestCreateOpensTheStockPosition(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.CreateFn = func(_ *gorm.DB, product model.Product) (model.Product, error) {
		product.Id = 11

		return product, nil
	}

	_, err := service.Create(callerContext(), createReq(nil))

	require.NoError(t, err)

	// A new product starts with a stock position of its own, so the quantity has
	// a row to move from the first time stock arrives. The key it points at is the
	// one the database assigned, not the zero the request came in with.
	require.Len(t, deps.stockPosition.CreateCalls, 1)
	position := deps.stockPosition.CreateCalls[0]
	require.Equal(t, int64(11), position.ProductId)
	require.Zero(t, position.Quantity)

	// The position carries the same audit trail as every other row this service
	// writes. Left unstamped it would land inactive, with a zero actor and a year
	// 0001 timestamp, and the inventory service shares this table.
	require.True(t, position.IsActive)
	require.False(t, position.IsDeleted)
	require.Equal(t, caller, position.CreatedBy)
	require.Equal(t, caller, position.UpdatedBy)
	require.False(t, position.CreatedAt.IsZero())
	require.False(t, position.UpdatedAt.IsZero())

	// Both rows are written inside the one transaction.
	require.Equal(t, 1, deps.database.Transactions)
}

func TestCreateWritesNoStockPositionWhenTheProductFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.CreateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	got, err := service.Create(callerContext(), createReq(nil))

	// The failure of the first write is what the caller sees, and the position is
	// never opened against a product that does not exist.
	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.ProductCreateRes{}, got)
	require.Empty(t, deps.stockPosition.CreateCalls)
}

func TestCreateReportsTheProductFailureEvenWhenTheStockPositionWouldSucceed(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.CreateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}
	deps.stockPosition.CreateFn = func(_ *gorm.DB, position model.StockPosition) (model.StockPosition, error) {
		return position, nil
	}

	_, err := service.Create(callerContext(), createReq(nil))

	// A second write that would have succeeded must not overwrite the error the
	// first one raised, which is how a failed insert once read as a success.
	require.ErrorIs(t, err, errDatabase)
}

func TestCreateWhenTheStockPositionFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}
	deps.stockPosition.CreateFn = func(*gorm.DB, model.StockPosition) (model.StockPosition, error) {
		return model.StockPosition{}, errDatabase
	}

	got, err := service.Create(callerContext(), createReq(uploadedImage()))

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.ProductCreateRes{}, got)

	// The image the failed write already put in the bucket is cleaned up again.
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Removed)
}

func TestCreateWithAnImage(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.storage.UploadFn = func(_ context.Context, folder string, _ *multipart.FileHeader) (string, error) {
		require.Equal(t, constant.ImageFolder, folder)

		return "product/baru.png", nil
	}

	got, err := service.Create(callerContext(), createReq(uploadedImage()))

	require.NoError(t, err)

	// The image is uploaded before the row is written, so the stored url points at
	// an object that is really in the bucket.
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Uploaded)
	require.Len(t, deps.products.CreateCalls, 1)
	require.Equal(t, "http://storage.test/bucket/product/baru.png", deps.products.CreateCalls[0].ImageURL)
	require.Equal(t, "http://storage.test/bucket/product/baru.png", got.ImageUrl)

	// Nothing is cleaned up when the write succeeds.
	require.Empty(t, deps.storage.Removed)
}

func TestCreateWithAnUnknownCategory(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.categories.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, gorm.ErrRecordNotFound
	}

	got, err := service.Create(callerContext(), createReq(uploadedImage()))

	require.ErrorIs(t, err, gorm.ErrRecordNotFound)
	require.Equal(t, dto.ProductCreateRes{}, got)

	// The lookup fails before anything is uploaded or written.
	require.Empty(t, deps.suppliers.DetailCalls)
	require.Empty(t, deps.storage.Uploaded)
	require.Empty(t, deps.products.CreateCalls)
	require.Zero(t, deps.database.Transactions)
}

func TestCreateWithAnUnknownSupplier(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.suppliers.DetailFn = func(*gorm.DB, string, interface{}) (model.Supplier, error) {
		return model.Supplier{}, gorm.ErrRecordNotFound
	}

	_, err := service.Create(callerContext(), createReq(uploadedImage()))

	require.ErrorIs(t, err, gorm.ErrRecordNotFound)
	require.Empty(t, deps.storage.Uploaded)
	require.Empty(t, deps.products.CreateCalls)
}

func TestCreateWhenTheUploadFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "", errStorage
	}

	_, err := service.Create(callerContext(), createReq(uploadedImage()))

	require.ErrorIs(t, err, errStorage)

	// No row is written when its image could not be stored.
	require.Empty(t, deps.products.CreateCalls)
	require.Zero(t, deps.database.Transactions)
	require.Empty(t, deps.storage.Removed)
}

func TestCreateWhenTheWriteFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}
	deps.products.CreateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	got, err := service.Create(callerContext(), createReq(uploadedImage()))

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.ProductCreateRes{}, got)

	// The uploaded image is dropped again, a failed write must leave no orphan
	// file in the bucket.
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Removed)
}

func TestCreateWhenTheWriteFailsWithoutAnImage(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.CreateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err := service.Create(callerContext(), createReq(nil))

	require.ErrorIs(t, err, errDatabase)

	// There is no object to clean up, so the bucket is left alone.
	require.Empty(t, deps.storage.Removed)
}

func TestCreateWhenTheCommitFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}
	deps.database.TransactionFn = func(context.Context, func(tx *gorm.DB) error) error {
		return errDatabase
	}

	_, err := service.Create(callerContext(), createReq(uploadedImage()))

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Removed)
}

func TestCreateCleansTheImageUpWhenTheWritePanics(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}
	deps.products.CreateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		panic("the driver gave up")
	}

	require.PanicsWithValue(t, "the driver gave up", func() {
		_, _ = service.Create(callerContext(), createReq(uploadedImage()))
	})

	// The panic is reported to the caller, and the orphan file is gone.
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Removed)
}

func TestSearch(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.SearchFn = func(_ *gorm.DB, _ dto.ProductSearchFilter) ([]model.Product, error) {
		return []model.Product{existingProduct()}, nil
	}

	got, err := service.Search(callerContext(), dto.ProductSearchReq{
		Name:         "  kipas  ",
		Description:  "  angin  ",
		MinBuyPrice:  decimal.NewFromInt(10),
		MaxBuyPrice:  decimal.NewFromInt(500),
		MinSellPrice: decimal.NewFromInt(20),
		MaxSellPrice: decimal.NewFromInt(900),
		MinStock:     1,
		MaxStock:     99,
		Paging:       base.Paging{SortBy: "BUY_PRICE", SortOrder: "ASC", PageSize: 500},
	})

	require.NoError(t, err)
	require.Len(t, got.Data, 1)
	require.Equal(t, int64(7), got.Data[0].Id)

	// The filters reach the repository trimmed, and the paging defaults are
	// already filled in.
	require.Len(t, deps.products.SearchCalls, 1)
	filter := deps.products.SearchCalls[0]
	require.Equal(t, "kipas", filter.Name)
	require.Equal(t, "angin", filter.Description)
	require.True(t, decimal.NewFromInt(10).Equal(filter.MinBuyPrice))
	require.True(t, decimal.NewFromInt(500).Equal(filter.MaxBuyPrice))
	require.True(t, decimal.NewFromInt(20).Equal(filter.MinSellPrice))
	require.True(t, decimal.NewFromInt(900).Equal(filter.MaxSellPrice))
	require.Equal(t, 1, filter.MinStock)
	require.Equal(t, 99, filter.MaxStock)
	require.Equal(t, "buy_price", filter.SortBy)
	require.Equal(t, constant.SortOrderAsc, filter.SortOrder)
	require.Equal(t, constant.DefaultPage, filter.Page)
	require.Equal(t, constant.MaxPageSize, filter.PageSize)
}

func TestSearchFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.SearchFn = func(*gorm.DB, dto.ProductSearchFilter) ([]model.Product, error) {
		return nil, errDatabase
	}

	got, err := service.Search(callerContext(), dto.ProductSearchReq{})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.ProductSearchRes{}, got)
}

func TestUpdate(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}

	got, err := service.Update(callerContext(), updateReq(nil))

	require.NoError(t, err)
	require.Equal(t, "Kipas Angin Baru", got.Name)
	require.Equal(t, "Elektronik", got.Category)
	require.Equal(t, "PT Maju", got.Supplier)

	require.Len(t, deps.products.UpdateCalls, 1)
	written := deps.products.UpdateCalls[0]
	require.Equal(t, int64(7), written.Id)
	require.Equal(t, "Deskripsi baru", written.Description)
	require.True(t, decimal.RequireFromString("180.00").Equal(written.BuyPrice))
	require.True(t, decimal.RequireFromString("249.99").Equal(written.SellPrice))

	// The relations are re-pointed at the rows that were read.
	require.Equal(t, int64(3), written.CategoryId)
	require.Equal(t, int64(4), written.SupplierId)

	// The flags and the creation trail of the existing row survive.
	require.True(t, written.IsActive)
	require.Equal(t, existingActor, written.CreatedBy)
	require.Equal(t, caller, written.UpdatedBy)

	// A request without an image keeps the one the product already has, and
	// nothing is removed from the bucket.
	require.Equal(t, existingProduct().ImageURL, written.ImageURL)
	require.Empty(t, deps.storage.Uploaded)
	require.Empty(t, deps.storage.Removed)
	require.Equal(t, 1, deps.database.Transactions)
}

func TestUpdateWithANewImageDropsTheOldOne(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	deps.storage.UploadFn = func(_ context.Context, folder string, _ *multipart.FileHeader) (string, error) {
		require.Equal(t, constant.ImageFolder, folder)

		return "product/baru.png", nil
	}
	deps.storage.GetFn = func(fileURL string) string {
		require.Equal(t, existingProduct().ImageURL, fileURL)

		return "product/lama.png"
	}

	got, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.NoError(t, err)
	require.Equal(t, "http://storage.test/bucket/product/baru.png", got.ImageUrl)

	// The image being replaced is dropped only once the new row is committed.
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Uploaded)
	require.Equal(t, []string{"product/lama.png"}, deps.storage.Removed)
}

func TestUpdateWithANewImageOnAProductThatHadNone(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		product := existingProduct()
		product.ImageURL = ""

		return product, nil
	}
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}
	deps.storage.GetFn = func(string) string {
		return ""
	}

	_, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.NoError(t, err)

	// There is no previous object, so nothing is deleted.
	require.Empty(t, deps.storage.Removed)
}

func TestUpdateNotFound(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, gorm.ErrRecordNotFound
	}

	got, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.ErrorIs(t, err, exception.ErrNotFound)
	require.Equal(t, dto.ProductUpdateRes{}, got)
	require.Empty(t, deps.storage.Uploaded)
	require.Empty(t, deps.products.UpdateCalls)
}

func TestUpdateFailsToReadTheProduct(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err := service.Update(callerContext(), updateReq(nil))

	require.ErrorIs(t, err, errDatabase)
	require.Empty(t, deps.products.UpdateCalls)
}

func TestUpdateWithAnUnknownCategory(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	deps.categories.DetailFn = func(*gorm.DB, string, interface{}) (model.Category, error) {
		return model.Category{}, gorm.ErrRecordNotFound
	}

	_, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.ErrorIs(t, err, gorm.ErrRecordNotFound)
	require.Empty(t, deps.suppliers.DetailCalls)
	require.Empty(t, deps.storage.Uploaded)
	require.Empty(t, deps.products.UpdateCalls)
}

func TestUpdateWithAnUnknownSupplier(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	deps.suppliers.DetailFn = func(*gorm.DB, string, interface{}) (model.Supplier, error) {
		return model.Supplier{}, gorm.ErrRecordNotFound
	}

	_, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.ErrorIs(t, err, gorm.ErrRecordNotFound)
	require.Empty(t, deps.storage.Uploaded)
	require.Empty(t, deps.products.UpdateCalls)
}

func TestUpdateWhenTheUploadFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "", errStorage
	}

	_, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.ErrorIs(t, err, errStorage)

	// The row is left as it was, and the image it points at is still reachable.
	require.Empty(t, deps.products.UpdateCalls)
	require.Empty(t, deps.storage.Removed)
	require.Zero(t, deps.database.Transactions)
}

func TestUpdateWhenTheWriteFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	deps.products.UpdateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}

	_, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.ErrorIs(t, err, errDatabase)

	// The new image is dropped and the old one is kept, a failed update must leave
	// the product with a reachable image.
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Removed)
}

func TestUpdateWhenTheCommitFails(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}
	deps.database.TransactionFn = func(context.Context, func(tx *gorm.DB) error) error {
		return errDatabase
	}

	_, err := service.Update(callerContext(), updateReq(uploadedImage()))

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, []string{"product/baru.png"}, deps.storage.Removed)
}

func TestUpdateCleansTheImageUpWhenTheWritePanics(t *testing.T) {
	service, deps := newServiceWith(t)
	deps.products.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	deps.storage.UploadFn = func(context.Context, string, *multipart.FileHeader) (string, error) {
		return "product/baru.png", nil
	}
	deps.products.UpdateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		panic("the driver gave up")
	}

	require.PanicsWithValue(t, "the driver gave up", func() {
		_, _ = service.Update(callerContext(), updateReq(uploadedImage()))
	})

	require.Equal(t, []string{"product/baru.png"}, deps.storage.Removed)
}

func TestDetail(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}

	got, err := service.Detail(callerContext(), dto.ProductDetailReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Kipas Angin", got.Name)

	// The lookup is always made on the primary key.
	require.Equal(t, []mocks.DetailCall{{Param: "id", Value: int64(7)}}, repository.DetailCalls)
}

func TestDetailNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, gorm.ErrRecordNotFound
	}

	got, err := service.Detail(callerContext(), dto.ProductDetailReq{Id: 7})

	// The gorm error is translated into the exception the controller reports.
	require.ErrorIs(t, err, exception.ErrNotFound)
	require.Equal(t, dto.ProductDetailRes{}, got)
}

func TestDetailFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err := service.Detail(callerContext(), dto.ProductDetailReq{Id: 7})

	// Anything that is not a missing row is passed through untouched.
	require.ErrorIs(t, err, errDatabase)
}

func TestHistory(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return productWithStockHistory(), nil
	}

	got, err := service.History(callerContext(), dto.ProductHistoryReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Kipas Angin", got.Name)

	// The trail the endpoint exists for is mapped onto the response.
	require.Equal(t, 12, got.StockQuantity)
	require.Len(t, got.StockHistory, 1)
	require.Equal(t, "PO-2024-0001", got.StockHistory[0].DocumentNumber)

	// The lookup is made on the primary key, and unlike Detail it asks for the
	// stock trail to be preloaded.
	require.Equal(t, []mocks.DetailCall{{Param: "id", Value: int64(7), WithStockHistory: true}},
		repository.DetailCalls)
}

func TestHistoryOfAProductThatNeverMoved(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		product := productWithStockHistory()
		product.Stock = &[]model.Stock{}
		product.StockPosition = nil

		return product, nil
	}

	got, err := service.History(callerContext(), dto.ProductHistoryReq{Id: 7})

	// A product with no movements yet answers with an empty trail, not an error.
	require.NoError(t, err)
	require.Equal(t, int64(7), got.Id)
	require.Zero(t, got.StockQuantity)
	require.Empty(t, got.StockHistory)
}

func TestHistoryNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, gorm.ErrRecordNotFound
	}

	got, err := service.History(callerContext(), dto.ProductHistoryReq{Id: 7})

	// The gorm error is translated into the exception the controller reports.
	require.ErrorIs(t, err, exception.ErrNotFound)
	require.Equal(t, dto.ProductHistoryRes{}, got)
}

func TestHistoryFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	got, err := service.History(callerContext(), dto.ProductHistoryReq{Id: 7})

	// Anything that is not a missing row is passed through untouched.
	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, dto.ProductHistoryRes{}, got)
}

func TestActivate(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		product := existingProduct()
		product.IsActive = false

		return product, nil
	}

	got, err := service.Activate(callerContext(), dto.ProductActivateReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, constant.Active, got.Status)
	require.Equal(t, int64(7), got.Id)

	require.Len(t, repository.UpdateCalls, 1)
	require.True(t, repository.UpdateCalls[0].IsActive)
	require.Equal(t, caller, repository.UpdateCalls[0].UpdatedBy)
}

func TestActivateAnAlreadyActiveProduct(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}

	_, err := service.Activate(callerContext(), dto.ProductActivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrAlreadyActive)
	require.Empty(t, repository.UpdateCalls)
}

func TestActivateNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, gorm.ErrRecordNotFound
	}

	_, err := service.Activate(callerContext(), dto.ProductActivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrNotFound)
}

func TestActivateFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err := service.Activate(callerContext(), dto.ProductActivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)

	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		product := existingProduct()
		product.IsActive = false

		return product, nil
	}
	repository.UpdateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err = service.Activate(callerContext(), dto.ProductActivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)
}

func TestDeactivate(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}

	got, err := service.Deactivate(callerContext(), dto.ProductDeactivateReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, constant.Inactive, got.Status)

	require.Len(t, repository.UpdateCalls, 1)
	require.False(t, repository.UpdateCalls[0].IsActive)
}

func TestDeactivateAnAlreadyInactiveProduct(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		product := existingProduct()
		product.IsActive = false

		return product, nil
	}

	_, err := service.Deactivate(callerContext(), dto.ProductDeactivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrAlreadyInactive)
	require.Empty(t, repository.UpdateCalls)
}

func TestDeactivateNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, gorm.ErrRecordNotFound
	}

	_, err := service.Deactivate(callerContext(), dto.ProductDeactivateReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrNotFound)
}

func TestDeactivateFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err := service.Deactivate(callerContext(), dto.ProductDeactivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)

	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	repository.UpdateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err = service.Deactivate(callerContext(), dto.ProductDeactivateReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)
}

func TestDelete(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}

	got, err := service.Delete(callerContext(), dto.ProductDeleteReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, constant.Delete, got.Status)

	// The row is flagged rather than removed, and it keeps the active flag it had.
	require.Len(t, repository.UpdateCalls, 1)
	require.True(t, repository.UpdateCalls[0].IsDeleted)
	require.True(t, repository.UpdateCalls[0].IsActive)
}

func TestDeleteNotFound(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, gorm.ErrRecordNotFound
	}

	_, err := service.Delete(callerContext(), dto.ProductDeleteReq{Id: 7})

	require.ErrorIs(t, err, exception.ErrNotFound)
	require.Empty(t, repository.UpdateCalls)
}

func TestDeleteFails(t *testing.T) {
	service, repository, _ := newService(t)
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err := service.Delete(callerContext(), dto.ProductDeleteReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)

	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}
	repository.UpdateFn = func(*gorm.DB, model.Product) (model.Product, error) {
		return model.Product{}, errDatabase
	}

	_, err = service.Delete(callerContext(), dto.ProductDeleteReq{Id: 7})
	require.ErrorIs(t, err, errDatabase)
}

func TestTheOrmIsTakenFromTheRequestContext(t *testing.T) {
	service, repository, database := newService(t)

	type key struct{}
	ctx := context.WithValue(callerContext(), key{}, "request")

	var seen context.Context
	database.OrmFn = func(ctx context.Context) *gorm.DB {
		seen = ctx

		return nil
	}
	repository.DetailFn = func(*gorm.DB, string, interface{}, bool) (model.Product, error) {
		return existingProduct(), nil
	}

	_, err := service.Detail(ctx, dto.ProductDetailReq{Id: 7})

	require.NoError(t, err)
	require.Equal(t, "request", seen.Value(key{}))
}
