package product

import (
	"mime/multipart"
	"net/textproto"
	"testing"
	"time"

	"product-service/internal/constant"
	"product-service/internal/dto/base"
	"product-service/internal/model"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/require"
)

func id(value int64) *int64 {
	return &value
}

func image(filename string, size int64) *multipart.FileHeader {
	return &multipart.FileHeader{
		Filename: filename,
		Header:   textproto.MIMEHeader{"Content-Type": []string{"image/png"}},
		Size:     size,
	}
}

func sampleProduct() model.Product {
	created := time.Date(2024, time.January, 2, 3, 4, 5, 0, time.UTC)

	return model.Product{
		Id:          7,
		Name:        "Kipas Angin",
		Description: "Kipas angin berdiri",
		Price:       decimal.RequireFromString("199.99"),
		ImageURL:    "http://storage.test/bucket/product/1.png",
		CategoryId:  3,
		SupplierId:  4,
		Base: model.Base{
			IsActive:  true,
			CreatedAt: created,
			UpdatedAt: created.Add(time.Hour),
		},
		Category: &model.Category{Id: 3, Name: "Elektronik"},
		Supplier: &model.Supplier{Id: 4, Name: "PT Maju"},
	}
}

func TestCreateReqToObjectModel(t *testing.T) {
	before := time.Now()
	got := ProductCreateReq{
		Name:        "Kipas",
		Description: "Kipas angin",
		Price:       decimal.NewFromInt(100),
		CategoryId:  id(3),
		SupplierId:  id(4),
	}.ToObjectModel()

	require.Equal(t, "Kipas", got.Name)
	require.Equal(t, int64(3), got.CategoryId)
	require.Equal(t, int64(4), got.SupplierId)
	require.True(t, got.IsActive)
	require.False(t, got.CreatedAt.Before(before))

	// A request without a relation leaves the foreign key at zero rather than
	// dereferencing a nil pointer.
	bare := ProductCreateReq{Name: "Kipas", Price: decimal.NewFromInt(1)}.ToObjectModel()
	require.Zero(t, bare.CategoryId)
	require.Zero(t, bare.SupplierId)
}

func TestCreateReqValidate(t *testing.T) {
	valid := ProductCreateReq{
		Name:       "Kipas",
		Price:      decimal.NewFromInt(100),
		CategoryId: id(3),
		SupplierId: id(4),
	}

	tests := []struct {
		name    string
		mutate  func(*ProductCreateReq)
		wantErr string
	}{
		{name: "a complete request is accepted", mutate: func(*ProductCreateReq) {}},
		{name: "the name is required", mutate: func(r *ProductCreateReq) { r.Name = "" }, wantErr: "name is required"},
		{
			name:    "the category is required",
			mutate:  func(r *ProductCreateReq) { r.CategoryId = nil },
			wantErr: "category is required",
		},
		{
			name:    "the supplier is required",
			mutate:  func(r *ProductCreateReq) { r.SupplierId = nil },
			wantErr: "supplier is required",
		},
		{
			name:    "a zero price is rejected",
			mutate:  func(r *ProductCreateReq) { r.Price = decimal.Zero },
			wantErr: "price is required",
		},
		{
			name:    "a negative price is rejected",
			mutate:  func(r *ProductCreateReq) { r.Price = decimal.NewFromInt(-1) },
			wantErr: "price is required",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			request := valid
			test.mutate(&request)

			err := request.Validate()
			if test.wantErr == "" {
				require.NoError(t, err)

				return
			}

			require.EqualError(t, err, test.wantErr)
		})
	}
}

func TestValidateImage(t *testing.T) {
	tests := []struct {
		name    string
		image   *multipart.FileHeader
		wantErr string
	}{
		{name: "no image is allowed", image: nil},
		{name: "jpg", image: image("foto.jpg", 1024)},
		{name: "jpeg", image: image("foto.JPEG", 1024)},
		{name: "png", image: image("foto.PNG", 1024)},
		{name: "webp", image: image("foto.webp", 1024)},
		{name: "at the size limit", image: image("foto.png", constant.MaxImageSize)},
		{
			name:    "over the size limit",
			image:   image("foto.png", constant.MaxImageSize+1),
			wantErr: "image size must not exceed 5 MB",
		},
		{
			name:    "an unsupported format",
			image:   image("foto.gif", 1024),
			wantErr: "image format must be one of jpg, jpeg, png, or webp",
		},
		{
			name:    "no extension at all",
			image:   image("foto", 1024),
			wantErr: "image format must be one of jpg, jpeg, png, or webp",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			createErr := ProductCreateReq{Image: test.image}.ValidateImage()
			updateErr := ProductUpdateReq{Image: test.image}.ValidateImage()

			if test.wantErr == "" {
				require.NoError(t, createErr)
				require.NoError(t, updateErr)

				return
			}

			require.EqualError(t, createErr, test.wantErr)
			require.EqualError(t, updateErr, test.wantErr)
		})
	}
}

func TestAllowedImageExt(t *testing.T) {
	allowed := AllowedImageExt()

	require.True(t, allowed[".jpg"])
	require.True(t, allowed[".jpeg"])
	require.True(t, allowed[".png"])
	require.True(t, allowed[".webp"])
	require.False(t, allowed[".gif"])
}

func TestToProductCreateRes(t *testing.T) {
	product := sampleProduct()
	got := ToProductCreateRes(product)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Kipas Angin", got.Name)
	require.True(t, product.Price.Equal(got.Price))
	require.Equal(t, product.ImageURL, got.ImageUrl)
	require.Equal(t, "Elektronik", got.Category)
	require.Equal(t, "PT Maju", got.Supplier)
	require.True(t, got.IsActive)

	// A row whose relations were not preloaded reports empty names rather than
	// dereferencing a nil pointer.
	bare := sampleProduct()
	bare.Category, bare.Supplier = nil, nil
	bareRes := ToProductCreateRes(bare)
	require.Empty(t, bareRes.Category)
	require.Empty(t, bareRes.Supplier)
}

func TestToProductDetailRes(t *testing.T) {
	product := sampleProduct()
	product.IsDeleted = true

	got := ToProductDetailRes(product)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Elektronik", got.Category)
	require.Equal(t, "PT Maju", got.Supplier)
	require.True(t, got.IsDeleted)

	bare := sampleProduct()
	bare.Category, bare.Supplier = nil, nil
	bareRes := ToProductDetailRes(bare)
	require.Empty(t, bareRes.Category)
	require.Empty(t, bareRes.Supplier)
}

func TestToProductSearchRes(t *testing.T) {
	got := ToProductSearchRes([]model.Product{sampleProduct(), {Id: 8, Name: "Lampu"}})

	require.Len(t, got.Data, 2)
	require.Equal(t, int64(7), got.Data[0].Id)
	require.Equal(t, "Lampu", got.Data[1].Name)

	require.NotNil(t, ToProductSearchRes(nil).Data)
	require.Empty(t, ToProductSearchRes(nil).Data)
}

func TestSearchReqValidate(t *testing.T) {
	tests := []struct {
		name    string
		given   ProductSearchReq
		wantErr string
	}{
		{name: "an empty request applies no filter", given: ProductSearchReq{}},
		{
			name: "a complete request is accepted",
			given: ProductSearchReq{
				MinPrice: decimal.NewFromInt(10),
				MaxPrice: decimal.NewFromInt(20),
				MinStock: 1,
				MaxStock: 5,
				Paging:   base.Paging{SortBy: "PRICE", SortOrder: "DESC", Page: 1, PageSize: 10},
			},
		},
		{
			name:    "a negative min price is rejected",
			given:   ProductSearchReq{MinPrice: decimal.NewFromInt(-1)},
			wantErr: "price filter must not be negative",
		},
		{
			name:    "a negative max price is rejected",
			given:   ProductSearchReq{MaxPrice: decimal.NewFromInt(-1)},
			wantErr: "price filter must not be negative",
		},
		{
			name:    "an inverted price range is rejected",
			given:   ProductSearchReq{MinPrice: decimal.NewFromInt(30), MaxPrice: decimal.NewFromInt(20)},
			wantErr: "min_price must not be greater than max_price",
		},
		{
			name:  "a min price without a max one is accepted",
			given: ProductSearchReq{MinPrice: decimal.NewFromInt(30)},
		},
		{
			name:    "a negative min stock is rejected",
			given:   ProductSearchReq{MinStock: -1},
			wantErr: "stock filter must not be negative",
		},
		{
			name:    "a negative max stock is rejected",
			given:   ProductSearchReq{MaxStock: -1},
			wantErr: "stock filter must not be negative",
		},
		{
			name:    "an inverted stock range is rejected",
			given:   ProductSearchReq{MinStock: 9, MaxStock: 5},
			wantErr: "min_stock must not be greater than max_stock",
		},
		{
			name:  "a min stock without a max one is accepted",
			given: ProductSearchReq{MinStock: 9},
		},
		{
			name:    "an unknown sortBy is rejected",
			given:   ProductSearchReq{Paging: base.Paging{SortBy: "password"}},
			wantErr: "sort_by must be one of id, name, price, stock, created_at, or updated_at",
		},
		{
			name:    "an unknown sortOrder is rejected",
			given:   ProductSearchReq{Paging: base.Paging{SortOrder: "sideways"}},
			wantErr: "sort_order must be one of asc or desc",
		},
		{
			name:    "a negative page is rejected",
			given:   ProductSearchReq{Paging: base.Paging{Page: -1}},
			wantErr: "page and page_size must not be negative",
		},
		{
			name:    "a negative page size is rejected",
			given:   ProductSearchReq{Paging: base.Paging{PageSize: -1}},
			wantErr: "page and page_size must not be negative",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := test.given.Validate()
			if test.wantErr == "" {
				require.NoError(t, err)

				return
			}

			require.EqualError(t, err, test.wantErr)
		})
	}
}

func TestSearchReqNormalize(t *testing.T) {
	got := ProductSearchReq{
		Name:        "  kipas  ",
		Description: "  angin  ",
		Paging:      base.Paging{SortBy: "PRICE", SortOrder: "ASC", Page: 3, PageSize: 200},
	}.Normalize()

	require.Equal(t, "kipas", got.Name)
	require.Equal(t, "angin", got.Description)
	require.Equal(t, "price", got.SortBy)
	require.Equal(t, constant.SortOrderAsc, got.SortOrder)
	require.Equal(t, 3, got.Page)
	require.Equal(t, constant.MaxPageSize, got.PageSize)
}

func TestUpdateReqToProductModel(t *testing.T) {
	existing := sampleProduct()
	existing.CreatedBy = 42
	existing.UpdatedBy = 42

	before := time.Now()
	got := ProductUpdateReq{
		Id:          7,
		Name:        "Kipas Baru",
		Description: "Deskripsi baru",
		Price:       decimal.NewFromInt(250),
	}.ToProductModel(existing)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Kipas Baru", got.Name)
	require.True(t, decimal.NewFromInt(250).Equal(got.Price))

	// The image and the status flags the row already carries are preserved, only
	// the audit trail is stamped again.
	require.Equal(t, existing.ImageURL, got.ImageURL)
	require.True(t, got.IsActive)
	require.Equal(t, int64(42), got.CreatedBy)
	require.Equal(t, int64(1), got.UpdatedBy)
	require.False(t, got.UpdatedAt.Before(before))
}

func TestUpdateReqValidate(t *testing.T) {
	require.NoError(t, ProductUpdateReq{Name: "Kipas", Price: decimal.NewFromInt(1)}.Validate())
	require.EqualError(t, ProductUpdateReq{Price: decimal.NewFromInt(1)}.Validate(), "name is required")
	require.EqualError(t, ProductUpdateReq{Name: "Kipas"}.Validate(), "price is required")
	require.EqualError(t, ProductUpdateReq{Name: "Kipas", Price: decimal.NewFromInt(-5)}.Validate(),
		"price is required")
}

func TestToProductUpdateRes(t *testing.T) {
	product := sampleProduct()
	got := ToProductUpdateRes(product)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Elektronik", got.Category)
	require.Equal(t, "PT Maju", got.Supplier)
	require.Equal(t, product.ImageURL, got.ImageUrl)

	bare := sampleProduct()
	bare.Category, bare.Supplier = nil, nil
	bareRes := ToProductUpdateRes(bare)
	require.Empty(t, bareRes.Category)
	require.Empty(t, bareRes.Supplier)
}

func TestStatusMappers(t *testing.T) {
	product := sampleProduct()

	require.Equal(t, ProductActivateRes{Id: 7, Name: "Kipas Angin", Status: constant.Active},
		ToProductActivateRes(product))
	require.Equal(t, ProductDeactivateRes{Id: 7, Name: "Kipas Angin", Status: constant.Inactive},
		ToProductDeactivateRes(product))
	require.Equal(t, ProductDeleteRes{Id: 7, Name: "Kipas Angin", Status: constant.Delete},
		ToProductDeleteRes(product))
}

func TestAllowedSortBy(t *testing.T) {
	allowed := allowedSortBy()

	for _, field := range []string{"id", "name", "price", "stock", "created_at", "updated_at"} {
		require.True(t, allowed[field], field)
	}

	require.False(t, allowed["description"])
}
