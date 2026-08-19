package configuration

import (
	"context"
	"fmt"
	"log"
	"os"
	"strconv"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

var (
	Minio       *minio.Client
	MinioBucket string

	minioEndpoint string
	minioUseSSL   bool
)

// ConnectStorage to connect with MinIO as storage
func ConnectStorage() {
	minioEndpoint = os.Getenv("MINIO_ENDPOINT")
	accessKey := os.Getenv("MINIO_ACCESS_KEY")
	secretKey := os.Getenv("MINIO_SECRET_KEY")
	MinioBucket = os.Getenv("MINIO_BUCKET")
	minioUseSSL, _ = strconv.ParseBool(os.Getenv("MINIO_USE_SSL"))

	var err error
	Minio, err = minio.New(minioEndpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: minioUseSSL,
	})
	if err != nil {
		log.Fatalf("Failed to connect to MinIO: %v", err)
	}

	ctx := context.Background()
	exists, err := Minio.BucketExists(ctx, MinioBucket)
	if err != nil {
		log.Fatalf("Failed to check MinIO bucket %s: %v", MinioBucket, err)
	}

	if !exists {
		if err = Minio.MakeBucket(ctx, MinioBucket, minio.MakeBucketOptions{}); err != nil {
			log.Fatalf("Failed to create MinIO bucket %s: %v", MinioBucket, err)
		}

		log.Printf("MinIO bucket %s created", MinioBucket)
	}

	log.Println("Successfully connected to the MinIO storage")
}

// MinioObjectURL builds the publicly reachable URL of an object inside the bucket.
func MinioObjectURL(objectName string) string {
	scheme := "http"
	if minioUseSSL {
		scheme = "https"
	}

	return fmt.Sprintf("%s://%s/%s/%s", scheme, minioEndpoint, MinioBucket, objectName)
}
