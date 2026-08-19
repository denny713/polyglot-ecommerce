package controller

import (
	"product-service/internal/controller/product"

	"github.com/labstack/echo/v5"
	"github.com/labstack/echo/v5/middleware"
)

func Routes(e *echo.Echo) {
	e.Use(middleware.RequestLogger())
	e.GET("/", Health)
	e.GET("/health", Health)

	api := e.Group("/api")
	api.POST("/product", product.Create)
}
