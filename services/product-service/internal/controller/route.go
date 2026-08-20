package controller

import (
	"net/http"

	"product-service/docs"
	"product-service/internal/controller/product"

	"github.com/labstack/echo/v5"
	"github.com/labstack/echo/v5/middleware"
	swaggerFiles "github.com/swaggo/files"
)

// swaggerBasePath is the prefix the swagger routes are mounted on, the file
// server needs it to resolve the asset names.
const swaggerBasePath = "/api/swagger"

func Routes(e *echo.Echo) {
	e.Use(middleware.RequestLogger())

	e.GET("/", Health)
	e.GET("/health", Health)

	api := e.Group("/api")
	registerSwaggerRoutes(api)
	registerProductRoutes(api)
}

func registerProductRoutes(api *echo.Group) {
	group := api.Group("/product")

	group.POST("", product.Create)
	group.GET("/:id", product.Detail)
}

func registerSwaggerRoutes(api *echo.Group) {
	group := api.Group("/swagger")

	group.GET("/swagger.json", func(c *echo.Context) error {
		return c.JSONBlob(http.StatusOK, docs.Swagger)
	})

	group.GET("", func(c *echo.Context) error {
		return c.Redirect(http.StatusFound, swaggerBasePath+"/index.html")
	})

	group.GET("/index.html", func(c *echo.Context) error {
		return c.HTML(http.StatusOK, swaggerUIPage)
	})

	group.GET("/*", echo.WrapHandler(
		http.StripPrefix(swaggerBasePath+"/", http.FileServer(swaggerFiles.HTTP)),
	))
}
