package supplier

import (
	"errors"
	"testing"

	"product-service/internal/dto/base"
	"product-service/internal/dto/supplier"
	"product-service/internal/model"
	"product-service/internal/testutil"

	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

var errDatabase = errors.New("connection reset by peer")

func TestCreate(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`INSERT INTO "supplier"`, testutil.Rows("id").Add(int64(11)))

	got, err := NewRepository().Create(orm, model.Supplier{Name: "PT Maju", Base: model.PrePersist()})

	require.NoError(t, err)
	require.Equal(t, int64(11), got.Id)
	require.Equal(t, "PT Maju", got.Name)
	require.Equal(t, 1, fake.Commits())
	require.Contains(t, fake.Statements()[0].SQL, `RETURNING "id"`)
}

func TestCreateFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`INSERT INTO "supplier"`, errDatabase)

	got, err := NewRepository().Create(orm, model.Supplier{Name: "PT Maju"})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, model.Supplier{}, got)
	require.Equal(t, 1, fake.Rollbacks())
}

func TestDetail(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`SELECT \* FROM "supplier"`, testutil.Rows("id", "name", "email", "is_active", "is_deleted").
		Add(int64(7), "PT Maju", "sales@maju.test", true, false))
	fake.Query(`FROM "product"`, testutil.Rows("id", "name", "supplier_id").Add(int64(3), "Kipas", int64(7)))

	got, err := NewRepository().Detail(orm, "id", int64(7))

	require.NoError(t, err)
	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "sales@maju.test", got.Email)

	statements := fake.Statements()
	require.Len(t, statements, 2)
	require.Contains(t, statements[0].SQL, "id = $1 AND is_deleted = FALSE")

	// The products of the supplier are preloaded, the deleted ones left out.
	require.Contains(t, statements[1].SQL, `FROM "product"`)
	require.Contains(t, statements[1].SQL, "is_deleted = FALSE")
	require.NotNil(t, got.Products)
	require.Len(t, *got.Products, 1)
}

func TestDetailByAnotherColumn(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`SELECT \* FROM "supplier"`, testutil.Rows("id", "email").Add(int64(7), "sales@maju.test"))

	_, err := NewRepository().Detail(orm, "email", "sales@maju.test")

	require.NoError(t, err)
	require.Contains(t, fake.Statements()[0].SQL, "email = $1 AND is_deleted = FALSE")
	require.Equal(t, "sales@maju.test", fake.Statements()[0].Args[0])
}

func TestDetailNotFound(t *testing.T) {
	orm, _ := testutil.NewDB(t)

	_, err := NewRepository().Detail(orm, "id", int64(7))

	require.ErrorIs(t, err, gorm.ErrRecordNotFound)
}

func TestDetailFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`FROM "supplier"`, errDatabase)

	_, err := NewRepository().Detail(orm, "id", int64(7))

	require.ErrorIs(t, err, errDatabase)
}

func TestUpdate(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Exec(`UPDATE "supplier"`, 1)

	got, err := NewRepository().Update(orm, model.Supplier{
		Id:    3,
		Name:  "PT Maju Jaya",
		Email: "info@maju.test",
		Base:  model.Base{IsActive: true, UpdatedBy: 1},
	})

	require.NoError(t, err)
	require.Equal(t, "PT Maju Jaya", got.Name)

	statement := fake.Statements()[0]
	require.Contains(t, statement.SQL, `UPDATE "supplier" SET`)
	require.Contains(t, statement.SQL, "is_deleted = FALSE")
	require.Contains(t, statement.SQL, "contact_person")
	require.Contains(t, statement.SQL, "postal_code")
	require.NotContains(t, statement.SQL, "created_by")
	require.NotContains(t, statement.SQL, "created_at")
}

func TestUpdateFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`UPDATE "supplier"`, errDatabase)

	got, err := NewRepository().Update(orm, model.Supplier{Id: 3, Name: "PT Maju"})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, "PT Maju", got.Name)
}

func TestSearchAppliesEveryFilter(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`FROM "supplier"`, testutil.Rows("id", "name").Add(int64(1), "PT Maju"))

	got, err := NewRepository().Search(orm, supplier.SupplierSearchFilter{
		Name:          "maju",
		Phone:         "021",
		Email:         "sales",
		ContactPerson: "budi",
		Province:      "dki",
		City:          "jakarta",
		District:      "gambir",
		Subdistrict:   "petojo",
		PostalCode:    "10110",
		Paging:        base.Paging{SortBy: "city", SortOrder: "asc", Page: 2, PageSize: 20},
	})

	require.NoError(t, err)
	require.Len(t, got, 1)

	statement := fake.Statements()[0]
	for _, column := range []string{"supplier.name", "supplier.phone", "supplier.email",
		"supplier.contact_person", "supplier.province", "supplier.city", "supplier.district",
		"supplier.subdistrict", "supplier.postal_code"} {
		require.Contains(t, statement.SQL, column+" ILIKE", column)
	}

	require.Contains(t, statement.SQL, "ORDER BY supplier.city ASC")
	require.Contains(t, statement.SQL, "LIMIT")
	require.Contains(t, statement.SQL, "OFFSET")

	// Every filter is matched partially, and in the order the columns are listed.
	require.Equal(t, "%maju%", statement.Args[0])
	require.Equal(t, "%10110%", statement.Args[8])
}

func TestSearchWithoutFilters(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	got, err := NewRepository().Search(orm, supplier.SupplierSearchFilter{})

	require.NoError(t, err)
	require.NotNil(t, got)
	require.Empty(t, got)

	statement := fake.Statements()[0]
	require.NotContains(t, statement.SQL, "ILIKE")
	require.Contains(t, statement.SQL, "ORDER BY supplier.id DESC")
}

func TestSearchFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`FROM "supplier"`, errDatabase)

	got, err := NewRepository().Search(orm, supplier.SupplierSearchFilter{})

	require.ErrorIs(t, err, errDatabase)
	require.Nil(t, got)
}

func TestSortColumns(t *testing.T) {
	require.Equal(t, map[string]string{
		"id":             "supplier.id",
		"name":           "supplier.name",
		"email":          "supplier.email",
		"contact_person": "supplier.contact_person",
		"province":       "supplier.province",
		"city":           "supplier.city",
		"created_at":     "supplier.created_at",
		"updated_at":     "supplier.updated_at",
	}, sortColumns)
}
