package controller

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"product-service/internal/constant"
	"product-service/internal/controller/category"
	"product-service/internal/controller/product"
	"product-service/internal/controller/supplier"
	"product-service/internal/exception"
	"product-service/internal/mocks"
	"product-service/internal/testutil"
	"product-service/internal/token"

	"github.com/labstack/echo/v5"
	"github.com/stretchr/testify/require"
)

// services gathers the doubles the routed handlers delegate to, so a test can
// tell which one a request reached.
type services struct {
	category *mocks.CategoryService
	product  *mocks.ProductService
	supplier *mocks.SupplierService
	verifier *mocks.TokenVerifier
}

func newServer(t *testing.T) (*echo.Echo, *services) {
	t.Helper()

	doubles := &services{
		category: &mocks.CategoryService{},
		product:  &mocks.ProductService{},
		supplier: &mocks.SupplierService{},
		verifier: &mocks.TokenVerifier{},
	}

	e := echo.New()
	Routes(e, Controllers{
		Category: category.NewController(doubles.category),
		Product:  product.NewController(doubles.product),
		Supplier: supplier.NewController(doubles.supplier),
	}, doubles.verifier)

	return e, doubles
}

// authorized stamps the bearer header the guarded routes require, so a test
// about routing is not answered by the authorization middleware instead.
func authorized(request *http.Request) *http.Request {
	request.Header.Set("Authorization", "Bearer test-token")

	return request
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
			e.ServeHTTP(recorder, authorized(testutil.JSONRequest(test.method, test.target, `{"name":"Elektronik"}`)))

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
			e.ServeHTTP(recorder, authorized(testutil.JSONRequest(test.method, test.target, `{"name":"PT Maju"}`)))

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
			e.ServeHTTP(recorder, authorized(testutil.MultipartRequest(t, test.method, test.target, fields, nil)))

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
			e.ServeHTTP(recorder, authorized(httptest.NewRequest(test.method, test.target, nil)))

			require.Equal(t, 1, test.called(doubles), "the route did not reach its handler")
		})
	}
}

func TestTheSwaggerRoutes(t *testing.T) {
	e, _ := newServer(t)

	t.Run("the specification is served as json", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, authorized(httptest.NewRequest(http.MethodGet, "/api/swagger/swagger.json", nil)))

		require.Equal(t, http.StatusOK, recorder.Code)
		require.Contains(t, recorder.Header().Get(echo.HeaderContentType), echo.MIMEApplicationJSON)

		var specification map[string]interface{}
		require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &specification))
		require.Contains(t, specification, "paths")
	})

	t.Run("the group redirects to the ui", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, authorized(httptest.NewRequest(http.MethodGet, "/api/swagger", nil)))

		require.Equal(t, http.StatusFound, recorder.Code)
		require.Equal(t, "/api/swagger/index.html", recorder.Header().Get(echo.HeaderLocation))
	})

	t.Run("the ui page is served", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, authorized(httptest.NewRequest(http.MethodGet, "/api/swagger/index.html", nil)))

		require.Equal(t, http.StatusOK, recorder.Code)
		require.Contains(t, recorder.Body.String(), "swagger-ui")

		// The page reads its assets from the same group, so the documentation works
		// without reaching the internet.
		require.NotContains(t, recorder.Body.String(), "http://")
		require.NotContains(t, recorder.Body.String(), "https://")
	})

	t.Run("the ui assets are served from the embedded distribution", func(t *testing.T) {
		recorder := httptest.NewRecorder()
		e.ServeHTTP(recorder, authorized(httptest.NewRequest(http.MethodGet, "/api/swagger/swagger-ui.css", nil)))

		require.Equal(t, http.StatusOK, recorder.Code)
		require.NotEmpty(t, recorder.Body.Bytes())
	})
}

func TestAnUnknownRoute(t *testing.T) {
	e, _ := newServer(t)

	recorder := httptest.NewRecorder()
	e.ServeHTTP(recorder, authorized(httptest.NewRequest(http.MethodGet, "/api/nothing", nil)))

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

// guardedRoutes is every route of the service together with the roles it
// accepts, so a route added without a guard shows up as a failure here.
var guardedRoutes = []struct {
	method      string
	target      string
	adminOrUser bool
}{
	{method: http.MethodPost, target: "/api/category"},
	{method: http.MethodGet, target: "/api/category"},
	{method: http.MethodGet, target: "/api/category/7"},
	{method: http.MethodPut, target: "/api/category/7"},
	{method: http.MethodPut, target: "/api/category/activate/7"},
	{method: http.MethodPut, target: "/api/category/deactivate/7"},
	{method: http.MethodDelete, target: "/api/category/7"},

	{method: http.MethodPost, target: "/api/product"},
	{method: http.MethodGet, target: "/api/product"},
	// The one endpoint a plain user may read.
	{method: http.MethodGet, target: "/api/product/7", adminOrUser: true},
	{method: http.MethodGet, target: "/api/product/history/7"},
	{method: http.MethodPut, target: "/api/product/7"},
	{method: http.MethodPut, target: "/api/product/activate/7"},
	{method: http.MethodPut, target: "/api/product/deactivate/7"},
	{method: http.MethodDelete, target: "/api/product/7"},

	{method: http.MethodPost, target: "/api/supplier"},
	{method: http.MethodGet, target: "/api/supplier"},
	{method: http.MethodGet, target: "/api/supplier/7"},
	{method: http.MethodPut, target: "/api/supplier/7"},
	{method: http.MethodPut, target: "/api/supplier/activate/7"},
	{method: http.MethodPut, target: "/api/supplier/deactivate/7"},
	{method: http.MethodDelete, target: "/api/supplier/7"},
}

// asRole scripts the verifier to accept every token as the given roles.
func asRole(doubles *services, roles ...string) {
	doubles.verifier.VerifyFn = func(string) (token.Claims, error) {
		return token.Claims{Subject: mocks.DefaultSubject, Roles: roles}, nil
	}
}

func TestEveryApiRouteNeedsAToken(t *testing.T) {
	for _, route := range guardedRoutes {
		t.Run(route.method+" "+route.target, func(t *testing.T) {
			e, doubles := newServer(t)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, testutil.JSONRequest(route.method, route.target, `{}`))

			require.Equal(t, http.StatusUnauthorized, recorder.Code)
			require.Contains(t, recorder.Body.String(), exception.ErrTokenMissing.Message)
			require.Empty(t, doubles.verifier.VerifyCalls, "no token was there to verify")
		})
	}
}

func TestAnAdminReachesEveryApiRoute(t *testing.T) {
	for _, route := range guardedRoutes {
		t.Run(route.method+" "+route.target, func(t *testing.T) {
			e, doubles := newServer(t)
			asRole(doubles, constant.AdminRole)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, authorized(testutil.JSONRequest(route.method, route.target, `{}`)))

			require.NotEqual(t, http.StatusUnauthorized, recorder.Code)
			require.NotEqual(t, http.StatusForbidden, recorder.Code)
			require.Len(t, doubles.verifier.VerifyCalls, 1)
		})
	}
}

func TestAUserReachesOnlyTheProductDetail(t *testing.T) {
	for _, route := range guardedRoutes {
		t.Run(route.method+" "+route.target, func(t *testing.T) {
			e, doubles := newServer(t)
			asRole(doubles, constant.UserRole)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, authorized(testutil.JSONRequest(route.method, route.target, `{}`)))

			if route.adminOrUser {
				require.NotEqual(t, http.StatusForbidden, recorder.Code,
					"a user is allowed to read one product")

				return
			}

			require.Equal(t, http.StatusForbidden, recorder.Code,
				"only the product detail is open to a user")
			require.Contains(t, recorder.Body.String(), exception.ErrForbidden.Message)
		})
	}
}

func TestAUserIsRefusedTheProductHistory(t *testing.T) {
	e, doubles := newServer(t)
	asRole(doubles, constant.UserRole)

	recorder := httptest.NewRecorder()
	e.ServeHTTP(recorder, authorized(httptest.NewRequest(http.MethodGet, "/api/product/history/7", nil)))

	// The history shares the /product prefix with the detail but is its own
	// route, and it stays administrative.
	require.Equal(t, http.StatusForbidden, recorder.Code)
	require.Empty(t, doubles.product.HistoryCalls)
}

func TestARoleTheServiceDoesNotUseIsRefused(t *testing.T) {
	e, doubles := newServer(t)
	asRole(doubles, "offline_access", "uma_authorization")

	recorder := httptest.NewRecorder()
	e.ServeHTTP(recorder, authorized(httptest.NewRequest(http.MethodGet, "/api/product/7", nil)))

	require.Equal(t, http.StatusForbidden, recorder.Code)
	require.Empty(t, doubles.product.DetailCalls)
}

func TestTheHealthAndDocsRoutesNeedNoToken(t *testing.T) {
	for _, target := range []string{"/", "/health", "/api/swagger/swagger.json", "/api/swagger/index.html"} {
		t.Run(target, func(t *testing.T) {
			e, doubles := newServer(t)

			recorder := httptest.NewRecorder()
			e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, target, nil))

			// A probe and the documentation read no data, and a health check has no
			// token to offer.
			require.Equal(t, http.StatusOK, recorder.Code)
			require.Empty(t, doubles.verifier.VerifyCalls)
		})
	}
}

func TestTheTokenIsPassedToTheVerifierAndTheCallerReachesTheHandler(t *testing.T) {
	e, doubles := newServer(t)

	recorder := httptest.NewRecorder()
	request := testutil.JSONRequest(http.MethodGet, "/api/product/7", "")
	request.Header.Set("Authorization", "Bearer the-access-token")
	e.ServeHTTP(recorder, request)

	// The bearer prefix is stripped before the token is verified.
	require.Equal(t, []string{"the-access-token"}, doubles.verifier.VerifyCalls)
	require.Len(t, doubles.product.DetailCalls, 1)
}

// TestTheServedSpecSecuresEveryOperation guards the swagger annotations against
// the failure they had: the bearer scheme was defined, so the authorize dialog
// accepted a token, but no operation required it and the UI therefore sent no
// header. An endpoint added without @Security fails here once the docs are
// regenerated.
func TestTheServedSpecSecuresEveryOperation(t *testing.T) {
	e, _ := newServer(t)

	recorder := httptest.NewRecorder()
	e.ServeHTTP(recorder, httptest.NewRequest(http.MethodGet, "/api/swagger/swagger.json", nil))
	require.Equal(t, http.StatusOK, recorder.Code)

	var spec struct {
		SecurityDefinitions map[string]struct {
			Type string `json:"type"`
			Name string `json:"name"`
			In   string `json:"in"`
		} `json:"securityDefinitions"`
		Paths map[string]map[string]struct {
			Security  []map[string][]string `json:"security"`
			Responses map[string]any        `json:"responses"`
		} `json:"paths"`
	}
	require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &spec))

	// The scheme has to send the token in the header the middleware reads.
	scheme, ok := spec.SecurityDefinitions["BearerAuth"]
	require.True(t, ok, "the bearer scheme is missing from the spec")
	require.Equal(t, "apiKey", scheme.Type)
	require.Equal(t, "Authorization", scheme.Name)
	require.Equal(t, "header", scheme.In)

	require.NotEmpty(t, spec.Paths)

	operations := 0
	for path, methods := range spec.Paths {
		for method, operation := range methods {
			operations++

			t.Run(strings.ToUpper(method)+" "+path, func(t *testing.T) {
				require.Len(t, operation.Security, 1,
					"the operation declares no security requirement, so the UI sends no token")
				require.Contains(t, operation.Security[0], "BearerAuth")

				// What the guard can answer is what the docs promise.
				require.Contains(t, operation.Responses, "401")
				require.Contains(t, operation.Responses, "403")
			})
		}
	}

	require.Equal(t, len(guardedRoutes), operations,
		"every guarded route is documented, and nothing else is")
}
