package controller

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"product-service/internal/controller/category"
	"product-service/internal/controller/product"
	"product-service/internal/controller/supplier"
	"product-service/internal/mocks"
	"product-service/internal/testutil"

	"github.com/labstack/echo/v5"
	"github.com/stretchr/testify/require"
)

// services gathers the doubles the routed handlers delegate to, so a test can
// tell which one a request reached.
type services struct {
	category *mocks.CategoryService
	product  *mocks.ProductService
	supplier *mocks.SupplierService
}

func newServer(t *testing.T) (*echo.Echo, *services) {
	t.Helper()

	doubles := &services{
		category: &mocks.CategoryService{},
		product:  &mocks.ProductService{},
		supplier: &mocks.SupplierService{},
	}

	e := echo.New()
	Routes(e, Controllers{
		Category: category.NewController(doubles.category),
		Product:  product.NewController(doubles.product),
		Supplier: supplier.NewController(doubles.supplier),
	})

	return e, doubles
}

func TestHealth(t *testing.T) {
	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/health", nil), nil)

	require.NoError(t, Health(c))
	require.Equal(t, http.StatusOK, recorder.Code)

	var message string
	require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &message))
	require.Equal(t, "Service is running", message)
}

func TestTheHealthRoutes(t *testing.T) {
	e, _ := newServer(t)

	for _, target := range []string{"/", "/health"} {
		t.Run(target, func(t *testing.T) {
			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, target, nil))

			require.Equal(t, http.StatusOK, recorder.Code)
			require.Contains(t, recorder.Body.String(), "Service is running")
		})
	}
}

func TestTheCategoryRoutes(t *testing.T) {
	tests := []struct {
		method string
		target string
		called func(*services) int
	}{
		{method: http.MethodPost, target: "/api/category", called: func(s *services) int {
			return len(s.category.CreateCalls)
		}},
		{method: http.MethodGet, target: "/api/category", called: func(s *services) int {
			return len(s.category.SearchCalls)
		}},
		{method: http.MethodGet, target: "/api/category/7", called: func(s *services) int {
			return len(s.category.DetailCalls)
		}},
		{method: http.MethodPut, target: "/api/category/7", called: func(s *services) int {
			return len(s.category.UpdateCalls)
		}},
		{method: http.MethodPut, target: "/api/category/activate/7", called: func(s *services) int {
			return len(s.category.ActivateCalls)
		}},
		{method: http.MethodPut, target: "/api/category/deactivate/7", called: func(s *services) int {
			return len(s.category.DeactivateCalls)
		}},
		{method: http.MethodDelete, target: "/api/category/7", called: func(s *services) int {
			return len(s.category.DeleteCalls)
		}},
	}

	for _, test := range tests {
		t.Run(test.method+" "+test.target, func(t *testing.T) {
			e, doubles := newServer(t)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, testutil.JSONRequest(test.method, test.target, `{"name":"Elektronik"}`))

			require.Equal(t, 1, test.called(doubles), "the route did not reach its handler")
		})
	}
}

func TestTheSupplierRoutes(t *testing.T) {
	tests := []struct {
		method string
		target string
		called func(*services) int
	}{
		{method: http.MethodPost, target: "/api/supplier", called: func(s *services) int {
			return len(s.supplier.CreateCalls)
		}},
		{method: http.MethodGet, target: "/api/supplier", called: func(s *services) int {
			return len(s.supplier.SearchCalls)
		}},
		{method: http.MethodGet, target: "/api/supplier/7", called: func(s *services) int {
			return len(s.supplier.DetailCalls)
		}},
		{method: http.MethodPut, target: "/api/supplier/7", called: func(s *services) int {
			return len(s.supplier.UpdateCalls)
		}},
		{method: http.MethodPut, target: "/api/supplier/activate/7", called: func(s *services) int {
			return len(s.supplier.ActivateCalls)
		}},
		{method: http.MethodPut, target: "/api/supplier/deactivate/7", called: func(s *services) int {
			return len(s.supplier.DeactivateCalls)
		}},
		{method: http.MethodDelete, target: "/api/supplier/7", called: func(s *services) int {
			return len(s.supplier.DeleteCalls)
		}},
	}

	for _, test := range tests {
		t.Run(test.method+" "+test.target, func(t *testing.T) {
			e, doubles := newServer(t)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, testutil.JSONRequest(test.method, test.target, `{"name":"PT Maju"}`))

			require.Equal(t, 1, test.called(doubles), "the route did not reach its handler")
		})
	}
}

func TestTheProductRoutes(t *testing.T) {
	fields := map[string]string{
		"name":        "Kipas Angin",
		"price":       "199.99",
		"category_id": "3",
		"supplier_id": "4",
	}

	tests := []struct {
		method string
		target string
		called func(*services) int
	}{
		{method: http.MethodPost, target: "/api/product", called: func(s *services) int {
			return len(s.product.CreateCalls)
		}},
		{method: http.MethodPut, target: "/api/product/7", called: func(s *services) int {
			return len(s.product.UpdateCalls)
		}},
	}

	for _, test := range tests {
		t.Run(test.method+" "+test.target, func(t *testing.T) {
			e, doubles := newServer(t)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, testutil.MultipartRequest(t, test.method, test.target, fields, nil))

			require.Equal(t, 1, test.called(doubles), "the route did not reach its handler")
		})
	}

	reads := []struct {
		method string
		target string
		called func(*services) int
	}{
		{method: http.MethodGet, target: "/api/product", called: func(s *services) int {
			return len(s.product.SearchCalls)
		}},
		{method: http.MethodGet, target: "/api/product/7", called: func(s *services) int {
			return len(s.product.DetailCalls)
		}},
		// The static segment wins over the /:id the detail route registers, so the
		// history endpoint is reachable rather than read as a product named
		// "history".
		{method: http.MethodGet, target: "/api/product/history/7", called: func(s *services) int {
			return len(s.product.HistoryCalls)
		}},
		{method: http.MethodPut, target: "/api/product/activate/7", called: func(s *services) int {
			return len(s.product.ActivateCalls)
		}},
		{method: http.MethodPut, target: "/api/product/deactivate/7", called: func(s *services) int {
			return len(s.product.DeactivateCalls)
		}},
		{method: http.MethodDelete, target: "/api/product/7", called: func(s *services) int {
			return len(s.product.DeleteCalls)
		}},
	}

	for _, test := range reads {
		t.Run(test.method+" "+test.target, func(t *testing.T) {
			e, doubles := newServer(t)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, httptest.NewRequest(test.method, test.target, nil))

			require.Equal(t, 1, test.called(doubles), "the route did not reach its handler")
		})
	}
}

func TestTheSwaggerRoutes(t *testing.T) {
	e, _ := newServer(t)

	t.Run("the specification is served as json", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/swagger/swagger.json", nil))

		require.Equal(t, http.StatusOK, recorder.Code)
		require.Contains(t, recorder.Header().Get(echo.HeaderContentType), echo.MIMEApplicationJSON)

		var specification map[string]interface{}
		require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &specification))
		require.Contains(t, specification, "paths")
	})

	t.Run("the group redirects to the ui", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/swagger", nil))

		require.Equal(t, http.StatusFound, recorder.Code)
		require.Equal(t, "/api/swagger/index.html", recorder.Header().Get(echo.HeaderLocation))
	})

	t.Run("the ui page is served", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/swagger/index.html", nil))

		require.Equal(t, http.StatusOK, recorder.Code)
		require.Contains(t, recorder.Body.String(), "swagger-ui")

		// The page reads its assets from the same group, so the documentation works
		// without reaching the internet.
		require.NotContains(t, recorder.Body.String(), "http://")
		require.NotContains(t, recorder.Body.String(), "https://")
	})

	t.Run("the ui assets are served from the embedded distribution", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/swagger/swagger-ui.css", nil))

		require.Equal(t, http.StatusOK, recorder.Code)
		require.NotEmpty(t, recorder.Body.Bytes())
	})
}

func TestAnUnknownRoute(t *testing.T) {
	e, _ := newServer(t)

	recorder := httptest.NewRecorder()
	e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/nothing", nil))

	require.Equal(t, http.StatusNotFound, recorder.Code)
}

func TestTheSwaggerPageIsSelfContained(t *testing.T) {
	// The constant is the page the route above serves, the assertions here are on
	// the markup itself.
	require.True(t, strings.HasPrefix(swaggerUIPage, "<!DOCTYPE html>"))
	require.Contains(t, swaggerUIPage, `href="./swagger-ui.css"`)
	require.Contains(t, swaggerUIPage, `src="./swagger-ui-bundle.js"`)
	require.Contains(t, swaggerUIPage, `url: "./swagger.json"`)
}
