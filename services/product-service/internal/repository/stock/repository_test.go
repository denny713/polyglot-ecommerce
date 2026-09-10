package stock

import (
	"errors"
	"testing"

	"product-service/internal/model"
	"product-service/internal/testutil"

	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

var errDatabase = errors.New("connection reset by peer")

func TestNewRepository(t *testing.T) {
	require.NotNil(t, NewRepository())
}

func TestCreate(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`INSERT INTO "stock_position"`, testutil.Rows("id").Add(int64(11)))

	got, err := NewRepository().Create(orm, model.StockPosition{
		ProductId: 7,
		Quantity:  0,
		Base:      model.PrePersist(),
	})

	require.NoError(t, err)
	require.Equal(t, int64(11), got.Id)
	require.Equal(t, int64(7), got.ProductId)
	require.Equal(t, 0, got.Quantity)
	require.Equal(t, 1, fake.Commits())

	// The insert lets the database assign the key and reads it back.
	statement := fake.Statements()[0]
	require.Contains(t, statement.SQL, `INSERT INTO "stock_position"`)
	require.Contains(t, statement.SQL, `RETURNING "id"`)
}

func TestCreateJoinsAnOngoingTransaction(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Query(`INSERT INTO "stock_position"`, testutil.Rows("id").Add(int64(11)))

	// The handle the caller passes in is the one the insert runs on, so a write
	// made inside a transaction does not open a second one of its own.
	err := orm.Transaction(func(tx *gorm.DB) error {
		_, err := NewRepository().Create(tx, model.StockPosition{ProductId: 7})

		return err
	})

	require.NoError(t, err)
	require.Equal(t, 1, fake.Commits())
	require.Equal(t, 1, fake.Calls(`INSERT INTO "stock_position"`))
}

func TestCreateFails(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	fake.Fail(`INSERT INTO "stock_position"`, errDatabase)

	got, err := NewRepository().Create(orm, model.StockPosition{ProductId: 7})

	// A failed insert reports the error and hands back nothing to write on.
	require.ErrorIs(t, err, errDatabase)
	require.Equal(t, model.StockPosition{}, got)
	require.Equal(t, 1, fake.Rollbacks())
}
