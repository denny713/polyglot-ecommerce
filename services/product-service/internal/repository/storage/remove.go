package storage

import (
	"context"

	"github.com/minio/minio-go/v7"
)

// Remove removes an object from the MinIO storage bucket.
func (s storage) Remove(ctx context.Context, objectName string) error {
	return s.client.RemoveObject(ctx, s.config.Bucket, objectName, minio.RemoveObjectOptions{})
}
