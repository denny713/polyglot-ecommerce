package configuration

import (
	"context"
	"log"
	"os"
	"strconv"

	"github.com/minio/minio-go/v7"
	"github.com/minio/minio-go/v7/pkg/credentials"
)

var (
	Minio       *minio.Client
	MinioBucket string
)

// StorageConfig describes where the objects live, it is what turns an object
// name into the URL a client can reach.
type StorageConfig struct {
	Endpoint string
	Bucket   string
	UseSSL   bool
}

// ConnectStorage to connect with MinIO as storage
func ConnectStorage() (*minio.Client, StorageConfig) {
	config := StorageConfig{
		Endpoint: os.Getenv("MINIO_ENDPOINT"),
		Bucket:   os.Getenv("MINIO_BUCKET"),
	}
	config.UseSSL, _ = strconv.ParseBool(os.Getenv("MINIO_USE_SSL"))

	accessKey := os.Getenv("MINIO_ACCESS_KEY")
	secretKey := os.Getenv("MINIO_SECRET_KEY")

	client, err := minio.New(config.Endpoint, &minio.Options{
		Creds:  credentials.NewStaticV4(accessKey, secretKey, ""),
		Secure: config.UseSSL,
	})
	if err != nil {
		log.Fatalf("Failed to connect to MinIO: %v", err)
	}

	Minio, MinioBucket = client, config.Bucket

	ctx := context.Background()
	exists, err := client.BucketExists(ctx, config.Bucket)
	if err != nil {
		log.Fatalf("Failed to check MinIO bucket %s: %v", config.Bucket, err)
	}

	if !exists {
		if err = client.MakeBucket(ctx, config.Bucket, minio.MakeBucketOptions{}); err != nil {
			log.Fatalf("Failed to create MinIO bucket %s: %v", config.Bucket, err)
		}

		log.Printf("MinIO bucket %s created", config.Bucket)
	}

	log.Println("Successfully connected to the MinIO storage")

	return client, config
}
