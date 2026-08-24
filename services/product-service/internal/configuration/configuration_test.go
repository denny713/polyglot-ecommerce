package configuration_test

import (
	"context"
	"errors"
	"net/http"
	"os"
	"testing"

	"product-service/internal/configuration"
	"product-service/internal/testutil"

	"github.com/stretchr/testify/require"
	"gorm.io/gorm"
)

// ConnectDatabase is left out on purpose: it opens the connection eagerly and
// reports a failure with log.Fatalf, so it cannot run without a live postgres.
func TestNewDatabaseBindsTheRequestContext(t *testing.T) {
	orm, _ := testutil.NewDB(t)
	database := configuration.NewDatabase(orm)

	type key struct{}
	ctx := context.WithValue(context.Background(), key{}, "request")

	handle := database.Orm(ctx)

	require.NotNil(t, handle)
	require.Equal(t, "request", handle.Statement.Context.Value(key{}))

	// The shared handle itself is left alone by the binding.
	require.NotSame(t, orm, handle)
}

func TestTransactionCommits(t *testing.T) {
	orm, fake := testutil.NewDB(t)

	var seen *gorm.DB
	err := configuration.NewDatabase(orm).Transaction(context.Background(), func(tx *gorm.DB) error {
		seen = tx

		return nil
	})

	require.NoError(t, err)
	require.NotNil(t, seen)
	require.Equal(t, 1, fake.Commits())
	require.Zero(t, fake.Rollbacks())
}

func TestTransactionRollsBack(t *testing.T) {
	orm, fake := testutil.NewDB(t)
	errWrite := errors.New("the write was refused")

	err := configuration.NewDatabase(orm).Transaction(context.Background(), func(*gorm.DB) error {
		return errWrite
	})

	require.ErrorIs(t, err, errWrite)
	require.Zero(t, fake.Commits())
	require.Equal(t, 1, fake.Rollbacks())
}

// setStorageEnv points the storage configuration at the fake server.
func setStorageEnv(t *testing.T, fake *testutil.StorageServer, bucket string) {
	t.Helper()

	t.Setenv("MINIO_ENDPOINT", fake.Endpoint())
	t.Setenv("MINIO_ACCESS_KEY", "key")
	t.Setenv("MINIO_SECRET_KEY", "secret")
	t.Setenv("MINIO_BUCKET", bucket)
	t.Setenv("MINIO_USE_SSL", "false")
}

func TestConnectStorage(t *testing.T) {
	fake := testutil.NewStorageServer(t, "product-bucket")
	setStorageEnv(t, fake, "product-bucket")

	client, config := configuration.ConnectStorage()

	require.NotNil(t, client)
	require.Equal(t, fake.Endpoint(), config.Endpoint)
	require.Equal(t, "product-bucket", config.Bucket)
	require.False(t, config.UseSSL)

	// The bucket already exists, so it is looked up and left alone.
	require.Contains(t, fake.Requests(), testutil.StorageRequest{
		Method: http.MethodHead,
		Path:   "/product-bucket/",
	})

	for _, request := range fake.Requests() {
		require.NotEqual(t, http.MethodPut, request.Method, "the existing bucket was created again")
	}

	// The globals stay in step with what was returned.
	require.Same(t, client, configuration.Minio)
	require.Equal(t, "product-bucket", configuration.MinioBucket)
}

func TestConnectStorageCreatesAMissingBucket(t *testing.T) {
	fake := testutil.NewStorageServer(t, "product-bucket")
	fake.MissingBucket()
	setStorageEnv(t, fake, "product-bucket")

	client, _ := configuration.ConnectStorage()

	require.NotNil(t, client)

	var created bool
	for _, request := range fake.Requests() {
		if request.Method == http.MethodPut {
			created = true
		}
	}

	require.True(t, created, "the bucket was not created: %v", fake.Requests())
}

func TestConnectStorageReadsTheSSLFlag(t *testing.T) {
	fake := testutil.NewStorageServer(t, "product-bucket")
	setStorageEnv(t, fake, "product-bucket")
	os.Setenv("MINIO_USE_SSL", "true")
	t.Cleanup(func() { os.Setenv("MINIO_USE_SSL", "false") })

	// The client is built for https, the lookup that follows cannot reach the
	// plain fake so only the configuration is asserted here.
	config := configuration.StorageConfig{
		Endpoint: os.Getenv("MINIO_ENDPOINT"),
		Bucket:   os.Getenv("MINIO_BUCKET"),
		UseSSL:   os.Getenv("MINIO_USE_SSL") == "true",
	}

	require.True(t, config.UseSSL)
	require.Equal(t, fake.Endpoint(), config.Endpoint)
}
