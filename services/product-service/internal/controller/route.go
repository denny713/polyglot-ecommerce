package controller

import (
	"net/http"

	"product-service/docs"
	"product-service/internal/controller/category"
	"product-service/internal/controller/product"
	"product-service/internal/controller/supplier"

	"github.com/labstack/echo/v5"
	"github.com/labstack/echo/v5/middleware"
	swaggerFiles "github.com/swaggo/files"
)

// Controllers gathers the handlers the routes are bound to, so the wiring done
// at start up is the only place that knows how they are built.
type Controllers struct {
	Category category.Controller
	Product  product.Controller
	Supplier supplier.Controller
}

func Routes(e *echo.Echo, controllers Controllers) {
	e.Use(middleware.RequestLogger())

	e.GET("/", Health)
	e.GET("/health", Health)

	api := e.Group("/api")
	registerSwaggerRoutes(api)
	registerCategoryRoutes(api, controllers.Category)
	registerProductRoutes(api, controllers.Product)
	registerSupplierRoutes(api, controllers.Supplier)
}

func registerCategoryRoutes(api *echo.Group, ctrl category.Controller) {
	group := api.Group("/category")

	group.POST("", ctrl.Create)
	group.GET("", ctrl.Search)
	group.GET("/:id", ctrl.Detail)
	group.PUT("/:id", ctrl.Update)
	group.PUT("/activate/:id", ctrl.Activate)
	group.PUT("/deactivate/:id", ctrl.Deactivate)
	group.DELETE("/:id", ctrl.Delete)
}

func registerProductRoutes(api *echo.Group, ctrl product.Controller) {
	group := api.Group("/product")

	group.POST("", ctrl.Create)
	group.GET("", ctrl.Search)
	group.GET("/:id", ctrl.Detail)
	group.PUT("/:id", ctrl.Update)
	group.PUT("/activate/:id", ctrl.Activate)
	group.PUT("/deactivate/:id", ctrl.Deactivate)
	group.DELETE("/:id", ctrl.Delete)
}

func registerSupplierRoutes(api *echo.Group, ctrl supplier.Controller) {
	group := api.Group("/supplier")

	group.POST("", ctrl.Create)
	group.GET("", ctrl.Search)
	group.GET("/:id", ctrl.Detail)
	group.PUT("/:id", ctrl.Update)
	group.PUT("/activate/:id", ctrl.Activate)
	group.PUT("/deactivate/:id", ctrl.Deactivate)
	group.DELETE("/:id", ctrl.Delete)
}

func registerSwaggerRoutes(api *echo.Group) {
	swaggerBasePath := "/api/swagger"
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
