package storage

import (
	"context"
	"fmt"
	"mime/multipart"
	"path/filepath"
	"product-service/internal/configuration"
	"strings"
	"time"

	"github.com/minio/minio-go/v7"
)

// Upload stores the uploaded file under the given folder and returns the object name.
func Upload(ctx context.Context, folder string, file *multipart.FileHeader) (string, error) {
	src, err := file.Open()
	if err != nil {
		return "", err
	}

	defer src.Close()

	objectName := fmt.Sprintf("%s/%d%s", folder, time.Now().UnixNano(), strings.ToLower(filepath.Ext(file.Filename)))
	contentType := file.Header.Get("Content-Type")
	if contentType == "" {
		contentType = "application/octet-stream"
	}

	_, err = configuration.Minio.PutObject(ctx, configuration.MinioBucket, objectName, src, file.Size, minio.PutObjectOptions{
		ContentType: contentType,
	})
	if err != nil {
		return "", err
	}

	return objectName, nil
}
