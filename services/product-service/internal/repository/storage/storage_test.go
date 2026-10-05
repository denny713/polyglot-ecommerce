package storage

import (
	"context"
	"mime/multipart"
	"net/http"
	"strings"
	"testing"

	"product-service/internal/configuration"
	"product-service/internal/testutil"

	"github.com/stretchr/testify/require"
)

// newStorage builds the repository over a fake object storage.
func newStorage(t *testing.T) (Storage, *testutil.StorageServer) {
	t.Helper()

	fake := testutil.NewStorageServer(t, "product-bucket")
	client, config := fake.Client(t)

	return NewStorage(client, config), fake
}

func imageHeader(t *testing.T, filename, contentType string) *multipart.FileHeader {
	t.Helper()

	req := testutil.MultipartRequest(t, http.MethodPost, "/", nil, &testutil.FilePart{
		Field:       "image",
		Filename:    filename,
		ContentType: contentType,
		Content:     []byte("fake image bytes"),
	})

	return testutil.FileHeader(t, req, "image")
}

func TestObjectURL(t *testing.T) {
	plain := NewStorage(nil, configuration.StorageConfig{Endpoint: "minio:9000", Bucket: "product-bucket"})
	require.Equal(t, "http://minio:9000/product-bucket/product/1.png", plain.ObjectURL("product/1.png"))

	secure := NewStorage(nil, configuration.StorageConfig{
		Endpoint: "cdn.test",
		Bucket:   "product-bucket",
		UseSSL:   true,
	})
	require.Equal(t, "https://cdn.test/product-bucket/product/1.png", secure.ObjectURL("product/1.png"))
}

func TestGet(t *testing.T) {
	repository := NewStorage(nil, configuration.StorageConfig{Endpoint: "minio:9000", Bucket: "product-bucket"})

	tests := []struct {
		name string
		url  string
		want string
	}{
		{
			name: "the scheme, the host and the bucket are dropped",
			url:  "http://minio:9000/product-bucket/product/1.png",
			want: "product/1.png",
		},
		{
			name: "the folder segments are kept",
			url:  "https://cdn.test/product-bucket/product/nested/1.png",
			want: "product/nested/1.png",
		},
		{name: "a bare path works too", url: "/product-bucket/product/1.png", want: "product/1.png"},
		{name: "an object name is returned as it is", url: "product/1.png", want: "product/1.png"},
		{name: "the surrounding blanks are trimmed", url: "  product/1.png  ", want: "product/1.png"},
		{
			name: "an escaped name is unescaped",
			url:  "http://minio:9000/product-bucket/product/kipas%20angin.png",
			want: "product/kipas angin.png",
		},
		{name: "an empty url has no object", url: "", want: ""},
		{name: "a blank url has no object", url: "   ", want: ""},
		{name: "the root has no object", url: "http://minio:9000/", want: ""},
		{
			// The bucket is only dropped as a leading segment of an object name, a
			// url that stops at the bucket has nothing to strip it from.
			name: "the bucket alone is left as it is",
			url:  "http://minio:9000/product-bucket",
			want: "product-bucket",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			require.Equal(t, test.want, repository.Get(test.url))
		})
	}
}

func TestGetWithoutABucketKeepsTheWholePath(t *testing.T) {
	// A configuration without a bucket cannot tell the bucket segment apart from
	// a folder, so the path is kept whole.
	repository := NewStorage(nil, configuration.StorageConfig{Endpoint: "minio:9000"})

	require.Equal(t, "product-bucket/product/1.png",
		repository.Get("http://minio:9000/product-bucket/product/1.png"))
}

func TestUpload(t *testing.T) {
	repository, fake := newStorage(t)

	objectName, err := repository.Upload(context.Background(), "product", imageHeader(t, "Kipas.PNG", "image/png"))

	require.NoError(t, err)

	// The object is named after the folder and the moment it was stored, and the
	// extension is lowered.
	require.True(t, strings.HasPrefix(objectName, "product/"), objectName)
	require.True(t, strings.HasSuffix(objectName, ".png"), objectName)

	var put bool
	for _, request := range fake.Requests() {
		if request.Method == http.MethodPut && strings.Contains(request.Path, objectName) {
			put = true
		}
	}

	require.True(t, put, "the object was not written: %v", fake.Requests())
}

func TestUploadWithoutAContentType(t *testing.T) {
	repository, _ := newStorage(t)

	objectName, err := repository.Upload(context.Background(), "product", imageHeader(t, "kipas.png", ""))

	require.NoError(t, err)
	require.NotEmpty(t, objectName)
}

func TestUploadFailsWhenTheFileCannotBeOpened(t *testing.T) {
	repository, fake := newStorage(t)

	// A header that carries neither content nor a temporary file cannot be read.
	objectName, err := repository.Upload(context.Background(), "product", &multipart.FileHeader{Filename: "kipas.png"})

	require.Error(t, err)
	require.Empty(t, objectName)
	require.Empty(t, fake.Requests())
}

func TestUploadFailsWhenTheStorageRejectsIt(t *testing.T) {
	repository, fake := newStorage(t)
	fake.Fail(http.StatusForbidden)

	objectName, err := repository.Upload(context.Background(), "product", imageHeader(t, "kipas.png", "image/png"))

	require.Error(t, err)
	require.Empty(t, objectName)
}

func TestRemove(t *testing.T) {
	repository, fake := newStorage(t)

	require.NoError(t, repository.Remove(context.Background(), "product/1.png"))

	var deleted bool
	for _, request := range fake.Requests() {
		if request.Method == http.MethodDelete && strings.Contains(request.Path, "product/1.png") {
			deleted = true
		}
	}

	require.True(t, deleted, "the object was not deleted: %v", fake.Requests())
}

func TestRemoveFails(t *testing.T) {
	repository, fake := newStorage(t)
	fake.Fail(http.StatusForbidden)

	require.Error(t, repository.Remove(context.Background(), "product/1.png"))
}

func TestGetKeepsAPathThatCannotBeUnescaped(t *testing.T) {
	repository := NewStorage(nil, configuration.StorageConfig{Endpoint: "minio:9000", Bucket: "product-bucket"})

	// A broken escape sequence is neither a url nor an unescapable path, so the
	// name is handed back as it was read.
	require.Equal(t, "product/%zz.png", repository.Get("product/%zz.png"))
}
