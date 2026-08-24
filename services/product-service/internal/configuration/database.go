package configuration

import (
	"context"
	"fmt"
	"log"
	"os"

	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

var DB *gorm.DB

// Database is the handle the services take on the persistence layer. The
// repositories still receive a *gorm.DB so a call can join a transaction, this
// interface is what decides which handle that is.
type Database interface {
	// Orm returns the shared gorm handle bound to the request context.
	Orm(ctx context.Context) *gorm.DB

	// Transaction runs fn inside a database transaction, committing when fn
	// returns nil and rolling back otherwise.
	Transaction(ctx context.Context, fn func(tx *gorm.DB) error) error
}

type database struct {
	db *gorm.DB
}

// NewDatabase wraps a gorm handle into the Database the services depend on.
func NewDatabase(db *gorm.DB) Database {
	return database{db: db}
}

// Orm returns the shared gorm handle bound to the request context, the
// repositories take it as a parameter so they never reach for the global.
func (d database) Orm(ctx context.Context) *gorm.DB {
	return d.db.WithContext(ctx)
}

// Transaction runs fn inside a database transaction.
func (d database) Transaction(ctx context.Context, fn func(tx *gorm.DB) error) error {
	return d.Orm(ctx).Transaction(fn)
}

// ConnectDatabase to connect with PostgreSQL as database
func ConnectDatabase() *gorm.DB {
	host := os.Getenv("DB_HOST")
	user := os.Getenv("DB_USER")
	password := os.Getenv("DB_PASSWORD")
	dbname := os.Getenv("DB_NAME")
	port := os.Getenv("DB_PORT")

	dsn := fmt.Sprintf("host=%s user=%s password=%s dbname=%s port=%s sslmode=disable TimeZone=Asia/Jakarta",
		host, user, password, dbname, port)

	var err error
	DB, err = gorm.Open(postgres.Open(dsn), &gorm.Config{})
	if err != nil {
		log.Fatalf("Failed to connect to database: %v", err)
	}

	log.Println("Successfully connected to the PostgreSQL database")

	return DB
}
