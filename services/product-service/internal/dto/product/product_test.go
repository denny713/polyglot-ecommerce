package product

import (
	"context"
	"mime/multipart"
	"net/textproto"
	"testing"
	"time"

	"product-service/internal/account"
	"product-service/internal/constant"
	"product-service/internal/dto/base"
	"product-service/internal/model"

	"github.com/google/uuid"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/require"
)

// caller is the account the token middleware would have put on the context, the
// subject of the verified access token the request carried.
var caller = uuid.MustParse("9a7c1d2e-0000-4000-8000-00000000009a")

func callerContext() context.Context {
	return account.WithUserLogin(context.Background(), caller)
}

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
		BuyPrice:    decimal.RequireFromString("150.00"),
		SellPrice:   decimal.RequireFromString("199.99"),
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
		BuyPrice:    decimal.NewFromInt(80),
		SellPrice:   decimal.NewFromInt(100),
		CategoryId:  id(3),
		SupplierId:  id(4),
	}.ToObjectModel(callerContext())

	require.Equal(t, "Kipas", got.Name)
	require.True(t, decimal.NewFromInt(80).Equal(got.BuyPrice))
	require.True(t, decimal.NewFromInt(100).Equal(got.SellPrice))
	require.Equal(t, int64(3), got.CategoryId)
	require.Equal(t, int64(4), got.SupplierId)
	require.True(t, got.IsActive)
	require.False(t, got.CreatedAt.Before(before))

	// The row is audited to the account the access token was issued for.
	require.Equal(t, caller, got.CreatedBy)
	require.Equal(t, caller, got.UpdatedBy)

	// A request without a relation leaves the foreign key at zero rather than
	// dereferencing a nil pointer.
	bare := ProductCreateReq{Name: "Kipas", BuyPrice: decimal.NewFromInt(1), SellPrice: decimal.NewFromInt(2)}.
		ToObjectModel(callerContext())
	require.Zero(t, bare.CategoryId)
	require.Zero(t, bare.SupplierId)
}

func TestCreateReqValidate(t *testing.T) {
	valid := ProductCreateReq{
		Name:       "Kipas",
		BuyPrice:   decimal.NewFromInt(80),
		SellPrice:  decimal.NewFromInt(100),
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
			name:    "a zero buy price is rejected",
			mutate:  func(r *ProductCreateReq) { r.BuyPrice = decimal.Zero },
			wantErr: "buy price is required",
		},
		{
			name:    "a negative buy price is rejected",
			mutate:  func(r *ProductCreateReq) { r.BuyPrice = decimal.NewFromInt(-1) },
			wantErr: "buy price is required",
		},
		{
			name:    "a zero sell price is rejected",
			mutate:  func(r *ProductCreateReq) { r.SellPrice = decimal.Zero },
			wantErr: "sell price is required",
		},
		{
			name:    "a negative sell price is rejected",
			mutate:  func(r *ProductCreateReq) { r.SellPrice = decimal.NewFromInt(-1) },
			wantErr: "sell price is required",
		},
		{
			// The margin the product is sold on is the difference between the two,
			// so a buy price that reaches the sell price leaves nothing to earn.
			name:    "a buy price above the sell price is rejected",
			mutate:  func(r *ProductCreateReq) { r.BuyPrice = decimal.NewFromInt(120) },
			wantErr: "buy price must be less than sell price",
		},
		{
			name:    "a buy price equal to the sell price is rejected",
			mutate:  func(r *ProductCreateReq) { r.BuyPrice = r.SellPrice },
			wantErr: "buy price must be less than sell price",
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
	require.True(t, product.BuyPrice.Equal(got.BuyPrice))
	require.True(t, product.SellPrice.Equal(got.SellPrice))
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
				MinBuyPrice:  decimal.NewFromInt(10),
				MaxBuyPrice:  decimal.NewFromInt(20),
				MinSellPrice: decimal.NewFromInt(30),
				MaxSellPrice: decimal.NewFromInt(40),
				MinStock:     1,
				MaxStock:     5,
				Paging:       base.Paging{SortBy: "BUY_PRICE", SortOrder: "DESC", Page: 1, PageSize: 10},
			},
		},
		{
			name:    "a negative min buy price is rejected",
			given:   ProductSearchReq{MinBuyPrice: decimal.NewFromInt(-1)},
			wantErr: "buy price filter must not be negative",
		},
		{
			name:    "a negative max buy price is rejected",
			given:   ProductSearchReq{MaxBuyPrice: decimal.NewFromInt(-1)},
			wantErr: "buy price filter must not be negative",
		},
		{
			name:    "an inverted buy price range is rejected",
			given:   ProductSearchReq{MinBuyPrice: decimal.NewFromInt(30), MaxBuyPrice: decimal.NewFromInt(20)},
			wantErr: "min_buy_price must not be greater than max_buy_price",
		},
		{
			name:  "a min buy price without a max one is accepted",
			given: ProductSearchReq{MinBuyPrice: decimal.NewFromInt(30)},
		},
		{
			name:    "a negative min sell price is rejected",
			given:   ProductSearchReq{MinSellPrice: decimal.NewFromInt(-1)},
			wantErr: "sell price filter must not be negative",
		},
		{
			name:    "a negative max sell price is rejected",
			given:   ProductSearchReq{MaxSellPrice: decimal.NewFromInt(-1)},
			wantErr: "sell price filter must not be negative",
		},
		{
			name:    "an inverted sell price range is rejected",
			given:   ProductSearchReq{MinSellPrice: decimal.NewFromInt(30), MaxSellPrice: decimal.NewFromInt(20)},
			wantErr: "min_sell_price must not be greater than max_sell_price",
		},
		{
			name:  "a min sell price without a max one is accepted",
			given: ProductSearchReq{MinSellPrice: decimal.NewFromInt(30)},
		},
		{
			// The two ranges are independent filters, a buy range that sits above
			// the sell range narrows the result rather than failing the request.
			name: "the buy and the sell range are validated on their own",
			given: ProductSearchReq{
				MinBuyPrice:  decimal.NewFromInt(100),
				MaxBuyPrice:  decimal.NewFromInt(200),
				MinSellPrice: decimal.NewFromInt(10),
				MaxSellPrice: decimal.NewFromInt(20),
			},
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
			wantErr: "sort_by must be one of id, name, buy_price, sell_price, stock, created_at, or updated_at",
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
		Paging:      base.Paging{SortBy: "SELL_PRICE", SortOrder: "ASC", Page: 3, PageSize: 200},
	}.Normalize()

	require.Equal(t, "kipas", got.Name)
	require.Equal(t, "angin", got.Description)
	require.Equal(t, "sell_price", got.SortBy)
	require.Equal(t, constant.SortOrderAsc, got.SortOrder)
	require.Equal(t, 3, got.Page)
	require.Equal(t, constant.MaxPageSize, got.PageSize)
}

func TestUpdateReqToProductModel(t *testing.T) {
	existing := sampleProduct()
	actor := uuid.MustParse("2b1f8f4a-0000-4000-8000-00000000002a")
	existing.CreatedBy = actor
	existing.UpdatedBy = actor

	before := time.Now()
	got := ProductUpdateReq{
		Id:          7,
		Name:        "Kipas Baru",
		Description: "Deskripsi baru",
		BuyPrice:    decimal.NewFromInt(200),
		SellPrice:   decimal.NewFromInt(250),
	}.ToProductModel(callerContext(), existing)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Kipas Baru", got.Name)
	require.True(t, decimal.NewFromInt(200).Equal(got.BuyPrice))
	require.True(t, decimal.NewFromInt(250).Equal(got.SellPrice))

	// The image and the status flags the row already carries are preserved, only
	// the audit trail is stamped again.
	require.Equal(t, existing.ImageURL, got.ImageURL)
	require.True(t, got.IsActive)
	require.Equal(t, actor, got.CreatedBy)
	require.Equal(t, caller, got.UpdatedBy)
	require.False(t, got.UpdatedAt.Before(before))
}

func TestUpdateReqValidate(t *testing.T) {
	valid := ProductUpdateReq{
		Name:       "Kipas",
		BuyPrice:   decimal.NewFromInt(1),
		SellPrice:  decimal.NewFromInt(2),
		CategoryId: id(3),
		SupplierId: id(4),
	}

	require.NoError(t, valid.Validate())

	missingName := valid
	missingName.Name = ""
	require.EqualError(t, missingName.Validate(), "name is required")

	// The service reads the rows both relations point at, so a request that omits
	// one is rejected rather than dereferenced.
	missingCategory := valid
	missingCategory.CategoryId = nil
	require.EqualError(t, missingCategory.Validate(), "category is required")

	missingSupplier := valid
	missingSupplier.SupplierId = nil
	require.EqualError(t, missingSupplier.Validate(), "supplier is required")

	missingBuy := valid
	missingBuy.BuyPrice = decimal.Zero
	require.EqualError(t, missingBuy.Validate(), "buying price is required")

	negativeBuy := valid
	negativeBuy.BuyPrice = decimal.NewFromInt(-5)
	require.EqualError(t, negativeBuy.Validate(), "buying price is required")

	missingSell := valid
	missingSell.SellPrice = decimal.Zero
	require.EqualError(t, missingSell.Validate(), "selling price is required")

	negativeSell := valid
	negativeSell.SellPrice = decimal.NewFromInt(-5)
	require.EqualError(t, negativeSell.Validate(), "selling price is required")

	// The margin the product is sold on is the difference between the two, so a
	// buy price that reaches the sell price leaves nothing to earn.
	noMargin := valid
	noMargin.BuyPrice = noMargin.SellPrice
	require.EqualError(t, noMargin.Validate(), "buy price must be less than sell price")

	inverted := valid
	inverted.BuyPrice = decimal.NewFromInt(3)
	require.EqualError(t, inverted.Validate(), "buy price must be less than sell price")
}

func TestToProductUpdateRes(t *testing.T) {
	product := sampleProduct()
	got := ToProductUpdateRes(product)

	require.Equal(t, int64(7), got.Id)
	require.True(t, product.BuyPrice.Equal(got.BuyPrice))
	require.True(t, product.SellPrice.Equal(got.SellPrice))
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

	for _, field := range []string{"id", "name", "buy_price", "sell_price", "stock", "created_at", "updated_at"} {
		require.True(t, allowed[field], field)
	}

	require.False(t, allowed["description"])
	require.False(t, allowed["price"])
}

// historyProduct is a product carrying one movement from a purchase order and
// one from a purchase return, so the whole mapping is exercised in one pass.
func historyProduct() model.Product {
	created := time.Date(2024, time.January, 2, 3, 4, 5, 0, time.UTC)
	moved := time.Date(2024, time.February, 3, 4, 5, 6, 0, time.UTC)

	orderedProduct := &model.Product{Id: 7, Name: "Kipas Angin"}

	return model.Product{
		Id:          7,
		Name:        "Kipas Angin",
		Description: "Kipas angin berdiri",
		BuyPrice:    decimal.RequireFromString("150.00"),
		SellPrice:   decimal.RequireFromString("199.99"),
		ImageURL:    "http://storage.test/bucket/product/kipas.png",
		Base: model.Base{
			IsActive:  true,
			CreatedAt: created,
			UpdatedAt: created,
		},
		Category:      &model.Category{Id: 3, Name: "Elektronik"},
		Supplier:      &model.Supplier{Id: 4, Name: "PT Maju"},
		StockPosition: &model.StockPosition{Id: 1, ProductId: 7, Quantity: 12},
		Stock: &[]model.Stock{
			{
				Id:              8,
				ProductId:       7,
				DocumentNumber:  "PO-2024-0001",
				DocumentType:    "PO",
				Activity:        "IN",
				Quantity:        15,
				PurchaseOrderId: 21,
				Base:            model.Base{CreatedAt: moved},
				PurchaseOrder: &model.PurchaseOrder{
					Id:              21,
					DocumentNumber:  "PO-2024-0001",
					Status:          "APPROVED",
					OrderGrandTotal: decimal.RequireFromString("3000.00"),
					RealGrandTotal:  decimal.RequireFromString("2900.00"),
					Note:            "Pesanan pertama",
					Supplier:        &model.Supplier{Id: 4, Name: "PT Maju"},
					PurchaseOrderDetail: &[]model.PurchaseOrderDetail{{
						Id:            31,
						OrderQuantity: 15,
						RealQuantity:  15,
						UnitPrice:     decimal.RequireFromString("200.00"),
						OrderSubtotal: decimal.RequireFromString("3000.00"),
						RealSubtotal:  decimal.RequireFromString("2900.00"),
						Note:          "Harga nego",
						Product:       orderedProduct,
					}},
				},
			},
			{
				Id:               9,
				ProductId:        7,
				DocumentNumber:   "PR-2024-0001",
				DocumentType:     "PR",
				Activity:         "OUT",
				Quantity:         3,
				PurchaseReturnId: 22,
				Base:             model.Base{CreatedAt: moved},
				PurchaseReturn: &model.PurchaseReturn{
					Id:             22,
					DocumentNumber: "PR-2024-0001",
					Status:         "APPROVED",
					GrandTotal:     decimal.RequireFromString("600.00"),
					Reason:         "Barang rusak",
					Note:           "Dikembalikan",
					Supplier:       &model.Supplier{Id: 4, Name: "PT Maju"},
					PurchaseReturnDetail: &[]model.PurchaseReturnDetail{{
						Id:        41,
						Quantity:  3,
						UnitPrice: decimal.RequireFromString("200.00"),
						Subtotal:  decimal.RequireFromString("600.00"),
						Reason:    "Barang rusak",
						Note:      "Retur sebagian",
						Product:   orderedProduct,
					}},
				},
			},
		},
	}
}

func TestToProductHistoryRes(t *testing.T) {
	got := ToProductHistoryRes(historyProduct())

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Kipas Angin", got.Name)
	require.Equal(t, "Kipas angin berdiri", got.Description)
	require.True(t, decimal.RequireFromString("150.00").Equal(got.BuyPrice))
	require.True(t, decimal.RequireFromString("199.99").Equal(got.SellPrice))
	require.Equal(t, "http://storage.test/bucket/product/kipas.png", got.ImageUrl)
	require.True(t, got.IsActive)
	require.False(t, got.IsDeleted)

	// The relations are flattened down to the name the response reports, and the
	// quantity comes off the stock position rather than the movements.
	require.Equal(t, "Elektronik", got.Category)
	require.Equal(t, "PT Maju", got.Supplier)
	require.Equal(t, 12, got.StockQuantity)

	require.Len(t, got.StockHistory, 2)
}

func TestToProductHistoryResMapsAPurchaseOrderMovement(t *testing.T) {
	got := ToProductHistoryRes(historyProduct()).StockHistory[0]

	require.Equal(t, int64(8), got.Id)
	require.Equal(t, "PO-2024-0001", got.DocumentNumber)
	require.Equal(t, "PO", got.DocumentType)
	require.Equal(t, "IN", got.Activity)
	require.Equal(t, 15, got.Quantity)
	require.Equal(t, time.Date(2024, time.February, 3, 4, 5, 6, 0, time.UTC), got.CreatedAt)

	// A movement that came from an order carries the order, and nothing else.
	require.Nil(t, got.PurchaseReturn)
	require.NotNil(t, got.PurchaseOrder)

	// The id reported is the one the stock row points at.
	require.Equal(t, int64(21), got.PurchaseOrder.Id)
	require.Equal(t, "PT Maju", got.PurchaseOrder.Supplier)
	require.Equal(t, "APPROVED", got.PurchaseOrder.Status)
	require.True(t, decimal.RequireFromString("3000.00").Equal(got.PurchaseOrder.OrderGrandTotal))
	require.True(t, decimal.RequireFromString("2900.00").Equal(got.PurchaseOrder.RealGrandTotal))
	require.Equal(t, "Pesanan pertama", got.PurchaseOrder.Note)

	require.Len(t, got.PurchaseOrder.Detail, 1)
	detail := got.PurchaseOrder.Detail[0]
	require.Equal(t, int64(31), detail.Id)
	require.Equal(t, "Kipas Angin", detail.Product)
	require.Equal(t, 15, detail.OrderQuantity)
	require.Equal(t, 15, detail.RealQuantity)
	require.True(t, decimal.RequireFromString("200.00").Equal(detail.UnitPrice))
	require.True(t, decimal.RequireFromString("3000.00").Equal(detail.OrderSubtotal))
	require.True(t, decimal.RequireFromString("2900.00").Equal(detail.RealSubtotal))
	require.Equal(t, "Harga nego", detail.Note)
}

func TestToProductHistoryResMapsAPurchaseReturnMovement(t *testing.T) {
	got := ToProductHistoryRes(historyProduct()).StockHistory[1]

	require.Equal(t, int64(9), got.Id)
	require.Equal(t, "PR-2024-0001", got.DocumentNumber)
	require.Equal(t, "PR", got.DocumentType)
	require.Equal(t, "OUT", got.Activity)
	require.Equal(t, 3, got.Quantity)

	// A movement that came from a return carries the return, and nothing else.
	require.Nil(t, got.PurchaseOrder)
	require.NotNil(t, got.PurchaseReturn)

	require.Equal(t, int64(22), got.PurchaseReturn.Id)
	require.Equal(t, "PT Maju", got.PurchaseReturn.Supplier)
	require.Equal(t, "APPROVED", got.PurchaseReturn.Status)
	require.True(t, decimal.RequireFromString("600.00").Equal(got.PurchaseReturn.GrandTotal))
	require.Equal(t, "Barang rusak", got.PurchaseReturn.Reason)
	require.Equal(t, "Dikembalikan", got.PurchaseReturn.Note)

	require.Len(t, got.PurchaseReturn.Detail, 1)
	detail := got.PurchaseReturn.Detail[0]
	require.Equal(t, int64(41), detail.Id)
	require.Equal(t, "Kipas Angin", detail.Product)
	require.Equal(t, 3, detail.Quantity)
	require.True(t, decimal.RequireFromString("200.00").Equal(detail.UnitPrice))
	require.True(t, decimal.RequireFromString("600.00").Equal(detail.Subtotal))
	require.Equal(t, "Barang rusak", detail.Reason)
	require.Equal(t, "Retur sebagian", detail.Note)
}

func TestToProductHistoryResWithoutRelations(t *testing.T) {
	product := historyProduct()
	product.Category = nil
	product.Supplier = nil
	product.StockPosition = nil
	product.Stock = &[]model.Stock{}

	got := ToProductHistoryRes(product)

	// A product whose relations were all soft deleted, and which never moved,
	// answers with the empty values rather than a partly filled response.
	require.Equal(t, int64(7), got.Id)
	require.Empty(t, got.Category)
	require.Empty(t, got.Supplier)
	require.Zero(t, got.StockQuantity)
	require.Empty(t, got.StockHistory)
}

func TestToProductHistoryResWithAMovementThatCarriesNoDocument(t *testing.T) {
	product := historyProduct()
	product.Stock = &[]model.Stock{{
		Id:             10,
		DocumentNumber: "ADJ-2024-0001",
		DocumentType:   "ADJ",
		Activity:       "IN",
		Quantity:       2,
	}}

	got := ToProductHistoryRes(product)

	// An adjustment points at neither an order nor a return, both stay empty.
	require.Len(t, got.StockHistory, 1)
	require.Equal(t, "ADJ-2024-0001", got.StockHistory[0].DocumentNumber)
	require.Nil(t, got.StockHistory[0].PurchaseOrder)
	require.Nil(t, got.StockHistory[0].PurchaseReturn)
}

func TestToProductHistoryResWithoutStockAtAll(t *testing.T) {
	product := historyProduct()
	product.Stock = nil

	// A product read without the stock trail preloaded leaves the pointer nil,
	// which must read as no history rather than bring the mapping down.
	require.NotPanics(t, func() { ToProductHistoryRes(product) })

	got := ToProductHistoryRes(product)
	require.Equal(t, int64(7), got.Id)
	require.Empty(t, got.StockHistory)
}

func TestToProductHistoryResWithASoftDeletedOrderSupplier(t *testing.T) {
	product := historyProduct()
	(*product.Stock)[0].PurchaseOrder.Supplier = nil

	got := ToProductHistoryRes(product)

	// The supplier of the order was soft deleted, so the preload filtered it out.
	// The order is still reported, with no supplier name on it.
	require.Len(t, got.StockHistory, 2)
	require.NotNil(t, got.StockHistory[0].PurchaseOrder)
	require.Empty(t, got.StockHistory[0].PurchaseOrder.Supplier)
	require.Equal(t, int64(21), got.StockHistory[0].PurchaseOrder.Id)
	require.Len(t, got.StockHistory[0].PurchaseOrder.Detail, 1)
}

func TestToProductHistoryResWithASoftDeletedReturnSupplier(t *testing.T) {
	product := historyProduct()
	(*product.Stock)[1].PurchaseReturn.Supplier = nil

	got := ToProductHistoryRes(product)

	require.NotNil(t, got.StockHistory[1].PurchaseReturn)
	require.Empty(t, got.StockHistory[1].PurchaseReturn.Supplier)
	require.Equal(t, int64(22), got.StockHistory[1].PurchaseReturn.Id)
	require.Len(t, got.StockHistory[1].PurchaseReturn.Detail, 1)
}

func TestToProductHistoryResWithASoftDeletedProductOnADetail(t *testing.T) {
	product := historyProduct()
	(*(*product.Stock)[0].PurchaseOrder.PurchaseOrderDetail)[0].Product = nil
	(*(*product.Stock)[1].PurchaseReturn.PurchaseReturnDetail)[0].Product = nil

	got := ToProductHistoryRes(product)

	// A soft deleted product leaves the line on the document, priced as it was,
	// with no product name.
	orderDetail := got.StockHistory[0].PurchaseOrder.Detail[0]
	require.Empty(t, orderDetail.Product)
	require.Equal(t, int64(31), orderDetail.Id)
	require.True(t, decimal.RequireFromString("200.00").Equal(orderDetail.UnitPrice))

	returnDetail := got.StockHistory[1].PurchaseReturn.Detail[0]
	require.Empty(t, returnDetail.Product)
	require.Equal(t, int64(41), returnDetail.Id)
	require.True(t, decimal.RequireFromString("600.00").Equal(returnDetail.Subtotal))
}

func TestToProductHistoryResWithNoDetailLinesOnADocument(t *testing.T) {
	product := historyProduct()
	(*product.Stock)[0].PurchaseOrder.PurchaseOrderDetail = nil
	(*product.Stock)[1].PurchaseReturn.PurchaseReturnDetail = nil

	got := ToProductHistoryRes(product)

	// A document whose lines were all soft deleted answers with an empty list
	// rather than a null, so the response shape does not change.
	require.NotNil(t, got.StockHistory[0].PurchaseOrder.Detail)
	require.Empty(t, got.StockHistory[0].PurchaseOrder.Detail)
	require.NotNil(t, got.StockHistory[1].PurchaseReturn.Detail)
	require.Empty(t, got.StockHistory[1].PurchaseReturn.Detail)
}

func TestToProductHistoryResSurvivesEveryRelationBeingGone(t *testing.T) {
	// The worst case the preloads can produce: the movement rows are there, but
	// every document, supplier and product behind them was soft deleted.
	product := historyProduct()
	product.Category = nil
	product.Supplier = nil
	product.StockPosition = nil
	product.Stock = &[]model.Stock{
		{Id: 8, DocumentNumber: "PO-2024-0001", PurchaseOrderId: 21, PurchaseOrder: &model.PurchaseOrder{Id: 21}},
		{Id: 9, DocumentNumber: "PR-2024-0001", PurchaseReturnId: 22, PurchaseReturn: &model.PurchaseReturn{Id: 22}},
	}

	require.NotPanics(t, func() { ToProductHistoryRes(product) })

	got := ToProductHistoryRes(product)
	require.Len(t, got.StockHistory, 2)
	require.Empty(t, got.StockHistory[0].PurchaseOrder.Supplier)
	require.Empty(t, got.StockHistory[0].PurchaseOrder.Detail)
	require.Empty(t, got.StockHistory[1].PurchaseReturn.Supplier)
	require.Empty(t, got.StockHistory[1].PurchaseReturn.Detail)
}
