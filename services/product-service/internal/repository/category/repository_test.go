package category

import (
	"context"
	"errors"
	"testing"

	"product-service/internal/dto/base"
	"product-service/internal/dto/category"
	"product-service/internal/model"
	"product-service/internal/testutil"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

var errDatabase = errors.New("connection reset by peer")

func TestCreate(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`INSERT INTO "category"`, testutil.Rows("id").Add(int64(11)))

	got, err := NewRepository().Create(orm, model.Category{Name: "Elektronik", Base: model.PrePersist(context.Background())})

	require.NoError(t, err)
	require.Equal(t, int64(11), got.Id)
	require.Equal(t, "Elektronik", got.Name)
	require.Equal(t, 1, fake.Commits())

	// The insert lets the database assign the key and reads it back.
	require.Contains(t, fake.Statements()[0].SQL, `RETURNING "id"`)
}

func TestCreateFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`INSERT INTO "category"`, errDatabase)

	got, err := NewRepository().Create(orm, model.Category{Name: "Elektronik"})

	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, model.Category{}, got)
	require.Equal(t, 1, fake.Rollbacks())
}

func TestDetail(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`SELECT \* FROM "category"`, testutil.Rows("id", "name", "description", "is_active", "is_deleted").
		Add(int64(7), "Elektronik", "Perangkat", true, false))
	fake.Query(`FROM "product"`, testutil.Rows("id", "name", "category_id").Add(int64(3), "Kipas", int64(7)))

	got, err := NewRepository().Detail(orm, "id", int64(7))

	require.NoError(t, err)
	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "Elektronik", got.Name)

	statements := fake.Statements()
	require.Len(t, statements, 2)

	// Only a row that is not soft deleted is a hit, and the lookup column is the
	// one the caller asked for.
	require.Contains(t, statements[0].SQL, "id = $1 AND is_deleted = FALSE")
	require.Equal(t, int64(7), statements[0].Args[0])

	// The products of the category are preloaded, the deleted ones left out.
	require.Contains(t, statements[1].SQL, `FROM "product"`)
	require.Contains(t, statements[1].SQL, "is_deleted = FALSE")
	require.NotNil(t, got.Products)
	require.Len(t, *got.Products, 1)
}

func TestDetailByAnotherColumn(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`SELECT \* FROM "category"`, testutil.Rows("id", "name").Add(int64(7), "Elektronik"))

	_, err := NewRepository().Detail(orm, "name", "Elektronik")

	require.NoError(t, err)
	require.Contains(t, fake.Statements()[0].SQL, "name = $1 AND is_deleted = FALSE")
	require.Equal(t, "Elektronik", fake.Statements()[0].Args[0])
}

func TestDetailNotFound(t *testing.T) {
	orm, _ := testutil.NewDB(t)

	got, err := NewRepository().Detail(orm, "id", int64(7))

	require.ErrorIs(t, err, gorm.ErrRecordNotFound)
	require.Zero(t, got.Id)
}

func TestDetailFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`FROM "category"`, errDatabase)

	_, err := NewRepository().Detail(orm, "id", int64(7))

	require.ErrorIs(t, err, errDatabase)
}

func TestUpdate(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Exec(`UPDATE "category"`, 1)

	got, err := NewRepository().Update(orm, model.Category{
		Id:   3,
		Name: "Elektronik Baru",
		Base: model.Base{IsActive: true, UpdatedBy: uuid.New()},
	})

	require.NoError(t, err)
	require.Equal(t, "Elektronik Baru", got.Name)

	statement := fake.Statements()[0]

	// Only the mapped columns are written, and a soft deleted row is never
	// updated again.
	require.Contains(t, statement.SQL, `UPDATE "category" SET`)
	require.Contains(t, statement.SQL, "is_deleted = FALSE")
	require.Contains(t, statement.SQL, "id = $")
	require.NotContains(t, statement.SQL, "created_by")
	require.NotContains(t, statement.SQL, "created_at")
}

func TestUpdateFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`UPDATE "category"`, errDatabase)

	got, err := NewRepository().Update(orm, model.Category{Id: 3, Name: "Elektronik"})

	require.ErrorIs(t, err, errDatabase)

	// The model is handed back as it came in, so the caller keeps what it had.
	require.Equal(t, "Elektronik", got.Name)
}

func TestSearch(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`FROM "category"`, testutil.Rows("id", "name").Add(int64(1), "Elektronik").Add(int64(2), "Baju"))

	got, err := NewRepository().Search(orm, category.CategorySearchFilter{
		Name:        "elek",
		Description: "perangkat",
		Paging:      base.Paging{SortBy: "name", SortOrder: "asc", Page: 3, PageSize: 5},
	})

	require.NoError(t, err)
	require.Len(t, got, 2)

	statement := fake.Statements()[0]
	require.Contains(t, statement.SQL, "category.is_deleted = FALSE")
	require.Contains(t, statement.SQL, "category.name ILIKE $1")
	require.Contains(t, statement.SQL, "category.description ILIKE $2")
	require.Contains(t, statement.SQL, "ORDER BY category.name ASC")
	require.Contains(t, statement.SQL, "LIMIT $3 OFFSET $4")
	require.Equal(t, []interface{}{"%elek%", "%perangkat%", 5, 10},
		[]interface{}{statement.Args[0], statement.Args[1], int(statement.Args[2].(int64)), int(statement.Args[3].(int64))})
}

func TestSearchWithoutFilters(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	got, err := NewRepository().Search(orm, category.CategorySearchFilter{})

	require.NoError(t, err)

	// An empty result is an empty slice, never a nil one.
	require.NotNil(t, got)
	require.Empty(t, got)

	statement := fake.Statements()[0]
	require.NotContains(t, statement.SQL, "ILIKE")
	require.NotContains(t, statement.SQL, "LIMIT")
	require.NotContains(t, statement.SQL, "OFFSET")

	// An unmapped sort key falls back to the id column, descending.
	require.Contains(t, statement.SQL, "ORDER BY category.id DESC")
}

func TestSearchOnTheFirstPageHasNoOffset(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	_, err := NewRepository().Search(orm, category.CategorySearchFilter{
		Paging: base.Paging{Page: 1, PageSize: 10},
	})

	require.NoError(t, err)
	require.Contains(t, fake.Statements()[0].SQL, "LIMIT")
	require.NotContains(t, fake.Statements()[0].SQL, "OFFSET")
}

func TestSearchFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`FROM "category"`, errDatabase)

	got, err := NewRepository().Search(orm, category.CategorySearchFilter{})

	require.ErrorIs(t, err, errDatabase)
	require.Nil(t, got)
}

func TestSortColumns(t *testing.T) {
	require.Equal(t, map[string]string{
		"id":          "category.id",
		"name":        "category.name",
		"description": "category.description",
		"created_at":  "category.created_at",
		"updated_at":  "category.updated_at",
	}, sortColumns)
}
