package controller

import (
	"net/http"

	"product-service/docs"
	"product-service/internal/constant"
	"product-service/internal/controller/category"
	"product-service/internal/controller/product"
	"product-service/internal/controller/supplier"
	"product-service/internal/token"

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

// guards holds the authorization middleware the routes are bound with. The
// service is administrative, so admin covers every endpoint and adminOrUser is
// the exception a route names for itself.
type guards struct {
	admin       echo.MiddlewareFunc
	adminOrUser echo.MiddlewareFunc
}

func Routes(e *echo.Echo, controllers Controllers, verifier token.Verifier) {
	e.Use(middleware.RequestLogger())

	e.GET("/", Health)
	e.GET("/health", Health)

	api := e.Group("/api")
	registerSwaggerRoutes(api)

	guard := guards{
		admin:       token.Authorize(verifier, constant.AdminRole),
		adminOrUser: token.Authorize(verifier, constant.AdminRole, constant.UserRole),
	}

	registerCategoryRoutes(api, controllers.Category, guard)
	registerProductRoutes(api, controllers.Product, guard)
	registerSupplierRoutes(api, controllers.Supplier, guard)
}

func registerCategoryRoutes(api *echo.Group, ctrl category.Controller, guard guards) {
	group := api.Group("/category")

	group.POST("", ctrl.Create, guard.admin)
	group.GET("", ctrl.Search, guard.admin)
	group.GET("/:id", ctrl.Detail, guard.admin)
	group.PUT("/:id", ctrl.Update, guard.admin)
	group.PUT("/activate/:id", ctrl.Activate, guard.admin)
	group.PUT("/deactivate/:id", ctrl.Deactivate, guard.admin)
	group.DELETE("/:id", ctrl.Delete, guard.admin)
}

func registerProductRoutes(api *echo.Group, ctrl product.Controller, guard guards) {
	group := api.Group("/product")

	group.POST("", ctrl.Create, guard.admin)
	group.GET("", ctrl.Search, guard.admin)
	group.GET("/:id", ctrl.Detail, guard.adminOrUser)
	group.GET("/history/:id", ctrl.History, guard.admin)
	group.PUT("/:id", ctrl.Update, guard.admin)
	group.PUT("/activate/:id", ctrl.Activate, guard.admin)
	group.PUT("/deactivate/:id", ctrl.Deactivate, guard.admin)
	group.DELETE("/:id", ctrl.Delete, guard.admin)
}

func registerSupplierRoutes(api *echo.Group, ctrl supplier.Controller, guard guards) {
	group := api.Group("/supplier")

	group.POST("", ctrl.Create, guard.admin)
	group.GET("", ctrl.Search, guard.admin)
	group.GET("/:id", ctrl.Detail, guard.admin)
	group.PUT("/:id", ctrl.Update, guard.admin)
	group.PUT("/activate/:id", ctrl.Activate, guard.admin)
	group.PUT("/deactivate/:id", ctrl.Deactivate, guard.admin)
	group.DELETE("/:id", ctrl.Delete, guard.admin)
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
