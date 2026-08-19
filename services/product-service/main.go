package main

import (
	"log"
	"os"
	"product-service/internal/configuration"
	"product-service/internal/controller"

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

	configuration.ConnectDatabase()
	configuration.ConnectStorage()

	e := echo.New()
	controller.Routes(e)

	if err := e.Start(":" + port); err != nil {
		e.Logger.Error("Failed to load server", "error", err)
	}
}
