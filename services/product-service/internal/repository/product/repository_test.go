package product

import (
	"errors"
	"testing"

	"product-service/internal/dto/base"
	"product-service/internal/dto/product"
	"product-service/internal/model"
	"product-service/internal/testutil"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

var errDatabase = errors.New("connection reset by peer")

func TestCreate(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`INSERT INTO "product"`, testutil.Rows("id").Add(int64(11)))

	got, err := NewRepository().Create(orm, model.Product{
		Name:  "Kipas",
		Price: decimal.RequireFromString("199.99"),
		Base:  model.PrePersist(),
	})

	require.NoError(t, err)
	require.Equal(t, int64(11), got.Id)
	require.Equal(t, "Kipas", got.Name)
	require.Equal(t, 1, fake.Commits())
	require.Contains(t, fake.Statements()[0].SQL, `RETURNING "id"`)
}

func TestCreateFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`INSERT INTO "product"`, errDatabase)

	got, err := NewRepository().Create(orm, model.Product{Name: "Kipas"})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, model.Product{}, got)
	require.Equal(t, 1, fake.Rollbacks())
}

func TestDetail(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`SELECT \* FROM "product"`,
		testutil.Rows("id", "name", "price", "category_id", "supplier_id", "is_active", "is_deleted").
			Add(int64(7), "Kipas", "199.99", int64(3), int64(4), true, false))
	fake.Query(`FROM "category"`, testutil.Rows("id", "name").Add(int64(3), "Elektronik"))
	fake.Query(`FROM "supplier"`, testutil.Rows("id", "name").Add(int64(4), "PT Maju"))
	fake.Query(`FROM "stock_position"`, testutil.Rows("id", "product_id", "quantity").Add(int64(1), int64(7), 12))
	fake.Query(`FROM "stock"`, testutil.Rows("id", "product_id", "quantity").Add(int64(1), int64(7), 12))

	got, err := NewRepository().Detail(orm, "id", int64(7))

	require.NoError(t, err)
	require.Equal(t, int64(7), got.Id)
	require.True(t, decimal.RequireFromString("199.99").Equal(got.Price))

	require.Contains(t, fake.Statements()[0].SQL, "id = $1 AND is_deleted = FALSE")

	// Every relation the response needs is preloaded, and the soft deleted rows
	// of each are left out.
	require.NotNil(t, got.Category)
	require.Equal(t, "Elektronik", got.Category.Name)
	require.NotNil(t, got.Supplier)
	require.Equal(t, "PT Maju", got.Supplier.Name)
	require.NotNil(t, got.StockPosition)
	require.Equal(t, 12, got.StockPosition.Quantity)
	require.NotNil(t, got.Stock)
}

func TestDetailByAnotherColumn(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`SELECT \* FROM "product"`, testutil.Rows("id", "name").Add(int64(7), "Kipas"))

	_, err := NewRepository().Detail(orm, "name", "Kipas")

	require.NoError(t, err)
	require.Contains(t, fake.Statements()[0].SQL, "name = $1 AND is_deleted = FALSE")
	require.Equal(t, "Kipas", fake.Statements()[0].Args[0])
}

func TestDetailNotFound(t *testing.T) {
	orm, _ := testutil.NewDB(t)

	_, err := NewRepository().Detail(orm, "id", int64(7))

	require.ErrorIs(t, err, gorm.ErrRecordNotFound)
}

func TestDetailFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`FROM "product"`, errDatabase)

	_, err := NewRepository().Detail(orm, "id", int64(7))

	require.ErrorIs(t, err, errDatabase)
}

func TestUpdate(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Exec(`UPDATE "product"`, 1)

	got, err := NewRepository().Update(orm, model.Product{
		Id:         3,
		Name:       "Kipas Baru",
		Price:      decimal.NewFromInt(250),
		ImageURL:   "http://storage.test/bucket/product/1.png",
		CategoryId: 5,
		SupplierId: 6,
		Base:       model.Base{IsActive: true, UpdatedBy: 1},

		// A relation the caller preloaded must not be written a second time.
		Category: &model.Category{Id: 5, Name: "Elektronik"},
		Supplier: &model.Supplier{Id: 6, Name: "PT Maju"},
	})

	require.NoError(t, err)
	require.Equal(t, "Kipas Baru", got.Name)

	statement := fake.Statements()[0]
	require.Contains(t, statement.SQL, `UPDATE "product" SET`)
	require.Contains(t, statement.SQL, "is_deleted = FALSE")
	require.Contains(t, statement.SQL, "image_url")
	require.Contains(t, statement.SQL, "category_id")
	require.Contains(t, statement.SQL, "supplier_id")
	require.NotContains(t, statement.SQL, "created_by")
	require.NotContains(t, statement.SQL, "created_at")

	// The preloaded relations are omitted, so no write reaches their tables.
	require.Equal(t, 0, fake.Calls(`(INSERT|UPDATE) INTO? "category"`))
	require.Equal(t, 0, fake.Calls(`(INSERT|UPDATE) INTO? "supplier"`))
}

func TestUpdateFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`UPDATE "product"`, errDatabase)

	got, err := NewRepository().Update(orm, model.Product{Id: 3, Name: "Kipas"})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, "Kipas", got.Name)
}

func TestSearchAppliesEveryFilter(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`FROM "product"`, testutil.Rows("id", "name").Add(int64(1), "Kipas"))

	got, err := NewRepository().Search(orm, product.ProductSearchFilter{
		Name:        "kipas",
		Description: "angin",
		MinPrice:    decimal.NewFromInt(10),
		MaxPrice:    decimal.NewFromInt(500),
		MinStock:    1,
		MaxStock:    99,
		Paging:      base.Paging{SortBy: "price", SortOrder: "asc", Page: 2, PageSize: 20},
	})

	require.NoError(t, err)
	require.Len(t, got, 1)

	statement := fake.Statements()[0]
	require.Contains(t, statement.SQL, "product.is_deleted = FALSE")
	require.Contains(t, statement.SQL, "product.name ILIKE")
	require.Contains(t, statement.SQL, "product.description ILIKE")
	require.Contains(t, statement.SQL, "product.price >=")
	require.Contains(t, statement.SQL, "product.price <=")
	require.Contains(t, statement.SQL, "JOIN stock_position ON stock_position.product_id = product.id")
	require.Contains(t, statement.SQL, "stock_position.quantity >=")
	require.Contains(t, statement.SQL, "stock_position.quantity <=")
	require.Contains(t, statement.SQL, "ORDER BY product.price ASC")
	require.Contains(t, statement.SQL, "LIMIT")
	require.Contains(t, statement.SQL, "OFFSET")
}

func TestSearchWithoutFilters(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	got, err := NewRepository().Search(orm, product.ProductSearchFilter{})

	require.NoError(t, err)
	require.NotNil(t, got)
	require.Empty(t, got)

	statement := fake.Statements()[0]
	require.NotContains(t, statement.SQL, "ILIKE")
	require.NotContains(t, statement.SQL, "product.price")
	require.NotContains(t, statement.SQL, "JOIN stock_position")
	require.Contains(t, statement.SQL, "ORDER BY product.id DESC")
}

func TestSearchJoinsTheStockPositionToSortOnIt(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	// Sorting on the stock needs the join even when no stock filter was given.
	_, err := NewRepository().Search(orm, product.ProductSearchFilter{
		Paging: base.Paging{SortBy: "stock", SortOrder: "desc"},
	})

	require.NoError(t, err)
	require.Contains(t, fake.Statements()[0].SQL, "JOIN stock_position")
	require.Contains(t, fake.Statements()[0].SQL, "ORDER BY stock_position.quantity DESC")
	require.NotContains(t, fake.Statements()[0].SQL, "stock_position.quantity >=")
}

func TestSearchJoinsTheStockPositionForAMaxStockOnly(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	_, err := NewRepository().Search(orm, product.ProductSearchFilter{MaxStock: 5})

	require.NoError(t, err)
	require.Contains(t, fake.Statements()[0].SQL, "JOIN stock_position")
	require.Contains(t, fake.Statements()[0].SQL, "stock_position.quantity <=")
	require.NotContains(t, fake.Statements()[0].SQL, "stock_position.quantity >=")
}

func TestSearchIgnoresANegativePriceFilter(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	// Only a positive bound narrows the query, a zero or negative one is no
	// filter at all.
	_, err := NewRepository().Search(orm, product.ProductSearchFilter{
		MinPrice: decimal.NewFromInt(-10),
		MaxPrice: decimal.Zero,
	})

	require.NoError(t, err)
	require.NotContains(t, fake.Statements()[0].SQL, "product.price")
}

func TestSearchFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`FROM "product"`, errDatabase)

	got, err := NewRepository().Search(orm, product.ProductSearchFilter{})

	require.ErrorIs(t, err, errDatabase)
	require.Nil(t, got)
}

func TestSortColumns(t *testing.T) {
	require.Equal(t, map[string]string{
		"id":         "product.id",
		"name":       "product.name",
		"price":      "product.price",
		"stock":      "stock_position.quantity",
		"created_at": "product.created_at",
		"updated_at": "product.updated_at",
	}, sortColumns)
}
