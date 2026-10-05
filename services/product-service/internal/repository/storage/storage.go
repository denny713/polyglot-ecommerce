package storage

import (
	"context"
	"fmt"
	"mime/multipart"

	"product-service/internal/configuration"

	"github.com/minio/minio-go/v7"
)

// Storage is the contract the product service takes on the object storage.
type Storage interface {
	Upload(ctx context.Context, folder string, file *multipart.FileHeader) (string, error)
	Remove(ctx context.Context, objectName string) error
	Get(fileURL string) string
	ObjectURL(objectName string) string
}

type storage struct {
	client *minio.Client
	config configuration.StorageConfig
}

// NewStorage builds the object storage repository.
func NewStorage(client *minio.Client, config configuration.StorageConfig) Storage {
	return storage{client: client, config: config}
}

// ObjectURL builds the publicly reachable URL of an object inside the bucket.
func (s storage) ObjectURL(objectName string) string {
	scheme := "http"
	if s.config.UseSSL {
		scheme = "https"
	}

	return fmt.Sprintf("%s://%s/%s/%s", scheme, s.config.Endpoint, s.config.Bucket, objectName)
}
