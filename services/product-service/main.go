package main

import (
	"log"
	"os"

	"product-service/internal/configuration"
	"product-service/internal/controller"
	categoryCtrl "product-service/internal/controller/category"
	productCtrl "product-service/internal/controller/product"
	supplierCtrl "product-service/internal/controller/supplier"
	categoryRepo "product-service/internal/repository/category"
	productRepo "product-service/internal/repository/product"
	stockRepo "product-service/internal/repository/stock"
	storageRepo "product-service/internal/repository/storage"
	supplierRepo "product-service/internal/repository/supplier"
	categorySvc "product-service/internal/service/category"
	productSvc "product-service/internal/service/product"
	supplierSvc "product-service/internal/service/supplier"

	"github.com/joho/godotenv"
	"github.com/labstack/echo/v5"
)

// @title Product Service API
// @version 1.0
// @description This is a Product Service API.
// @host localhost:7130
// @BasePath /
// @securityDefinitions.apikey BearerAuth
// @in header
// @name Authorization
// @description Provide access token with Bearer prefix, e.g. "Bearer {token}".
func main() {
	err := godotenv.Load()
	if err != nil {
		log.Println("Error load .env file")
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "7130"
	}

	orm := configuration.ConnectDatabase()
	minioClient, storageConfig := configuration.ConnectStorage()

	e := echo.New()
	controller.Routes(e, buildControllers(
		configuration.NewDatabase(orm),
		storageRepo.NewStorage(minioClient, storageConfig),
	), configuration.NewTokenVerifier())

	if err := e.Start(":" + port); err != nil {
		e.Logger.Error("Failed to load server", "error", err)
	}
}

// buildControllers wires the repositories and the services into the handlers the
// routes are bound to.
func buildControllers(database configuration.Database, storage storageRepo.Storage) controller.Controllers {
	categories := categoryRepo.NewRepository()
	products := productRepo.NewRepository()
	suppliers := supplierRepo.NewRepository()
	stockPosition := stockRepo.NewRepository()

	return controller.Controllers{
		Category: categoryCtrl.NewController(categorySvc.NewService(database, categories)),
		Product: productCtrl.NewController(
			productSvc.NewService(database, products, categories, suppliers, stockPosition, storage),
		),
		Supplier: supplierCtrl.NewController(supplierSvc.NewService(database, suppliers)),
	}
}
