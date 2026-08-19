package storage

import (
	"context"
	"product-service/internal/configuration"

	"github.com/minio/minio-go/v7"
)

func Remove(ctx context.Context, objectName string) error {
	return configuration.Minio.RemoveObject(ctx, configuration.MinioBucket, objectName, minio.RemoveObjectOptions{})
}
