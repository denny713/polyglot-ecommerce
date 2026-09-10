package category

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/base"
	categoryDto "product-service/internal/dto/category"
	"product-service/internal/exception"
	"product-service/internal/mocks"
	"product-service/internal/testutil"

	"github.com/labstack/echo/v5"
	"github.com/stretchr/testify/require"
)

var errDatabase = errors.New("connection reset by peer")

func newController() (Controller, *mocks.CategoryService) {
	service := &mocks.CategoryService{}

	return NewController(service), service
}

// body reads the response the handler wrote.
func body(t *testing.T, recorder *httptest.ResponseRecorder) dto.Response {
	t.Helper()

	var response dto.Response
	require.NoError(t, json.Unmarshal(recorder.Body.Bytes(), &response))

	return response
}

// httpError asserts err is an echo error carrying the given status and message.
func httpError(t *testing.T, err error, status int, message string) {
	t.Helper()

	var echoError *echo.HTTPError
	require.ErrorAs(t, err, &echoError)
	require.Equal(t, status, echoError.Code)
	require.Equal(t, message, echoError.Message)
}

func TestCreate(t *testing.T) {
	controller, service := newController()
	service.CreateFn = func(_ context.Context, request categoryDto.CategoryCreateReq) (categoryDto.CategoryCreateRes, error) {
		return categoryDto.CategoryCreateRes{Id: 11, Name: request.Name, IsActive: true}, nil
	}

	// The payload is padded on purpose, the handler trims it before it validates.
	c, recorder := testutil.NewContext(
		testutil.JSONRequest(http.MethodPost, "/api/category",
			`{"name":"  Elektronik  ","description":"  Perangkat  "}`), nil)

	require.NoError(t, controller.Create(c))
	require.Equal(t, http.StatusCreated, recorder.Code)

	response := body(t, recorder)
	require.Equal(t, http.StatusCreated, response.Status)
	require.Equal(t, constant.MsgSuccess, response.Message)
	require.Equal(t, "Elektronik", response.Data.(map[string]interface{})["name"])

	require.Equal(t, []categoryDto.CategoryCreateReq{{Name: "Elektronik", Description: "Perangkat"}},
		service.CreateCalls)
}

func TestCreateWithABodyThatIsNotJson(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(testutil.JSONRequest(http.MethodPost, "/api/category", `{`), nil)

	httpError(t, controller.Create(c), http.StatusBadRequest, "request body must be a valid json")
	require.Empty(t, service.CreateCalls)
}

func TestCreateWithoutAName(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPost, "/api/category", `{"name":"   "}`), nil)

	httpError(t, controller.Create(c), http.StatusBadRequest, "name is required")
	require.Empty(t, service.CreateCalls)
}

func TestCreateWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.CreateFn = func(context.Context, categoryDto.CategoryCreateReq) (categoryDto.CategoryCreateRes, error) {
		return categoryDto.CategoryCreateRes{}, errDatabase
	}

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPost, "/api/category", `{"name":"Elektronik"}`), nil)

	// An error that is not an exception is reported as an internal failure.
	httpError(t, controller.Create(c), http.StatusInternalServerError, errDatabase.Error())
}

func TestSearch(t *testing.T) {
	controller, service := newController()
	service.SearchFn = func(_ context.Context, _ categoryDto.CategorySearchReq) (categoryDto.CategorySearchRes, error) {
		return categoryDto.CategorySearchRes{Data: []categoryDto.CategoryDetailRes{{Id: 7, Name: "Elektronik"}}}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet,
		"/api/category?name=%20elektronik%20&description=%20perangkat%20&sort_by=name&sort_order=asc&page=2&page_size=5",
		nil), nil)

	require.NoError(t, controller.Search(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, constant.MsgSuccess, body(t, recorder).Message)

	// Every filter reaches the service trimmed.
	require.Equal(t, []categoryDto.CategorySearchReq{{
		Name:        "elektronik",
		Description: "perangkat",
		Paging:      base.Paging{SortBy: "name", SortOrder: "asc", Page: 2, PageSize: 5},
	}}, service.SearchCalls)
}

func TestSearchWithoutFilters(t *testing.T) {
	controller, service := newController()

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category", nil), nil)

	require.NoError(t, controller.Search(c))
	require.Equal(t, http.StatusOK, recorder.Code)

	// A missing parameter is left at its zero value, the service fills the
	// defaults in.
	require.Equal(t, []categoryDto.CategorySearchReq{{}}, service.SearchCalls)
}

func TestSearchWithAPageThatIsNotANumber(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category?page=abc", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest, "page must be a valid number")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWithAPageSizeThatIsNotANumber(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category?page_size=abc", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest, "page_size must be a valid number")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWithAnUnknownSortBy(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category?sort_by=password", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest,
		"sort_by must be one of id, name, description, created_at, or updated_at")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.SearchFn = func(context.Context, categoryDto.CategorySearchReq) (categoryDto.CategorySearchRes, error) {
		return categoryDto.CategorySearchRes{}, errDatabase
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category", nil), nil)

	httpError(t, controller.Search(c), http.StatusInternalServerError, errDatabase.Error())
}

func TestDetail(t *testing.T) {
	controller, service := newController()
	service.DetailFn = func(_ context.Context, request categoryDto.CategoryDetailReq) (categoryDto.CategoryDetailRes, error) {
		return categoryDto.CategoryDetailRes{Id: request.Id, Name: "Elektronik"}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Detail(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, constant.MsgSuccess, body(t, recorder).Message)
	require.Equal(t, []categoryDto.CategoryDetailReq{{Id: 7}}, service.DetailCalls)
}

func TestDetailWithAnInvalidIdentifier(t *testing.T) {
	for _, id := range []string{"abc", "0", "-1", ""} {
		t.Run("id="+id, func(t *testing.T) {
			controller, service := newController()

			c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category/"+id, nil),
				map[string]string{"id": id})

			httpError(t, controller.Detail(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
			require.Empty(t, service.DetailCalls)
		})
	}
}

func TestDetailNotFound(t *testing.T) {
	controller, service := newController()
	service.DetailFn = func(context.Context, categoryDto.CategoryDetailReq) (categoryDto.CategoryDetailRes, error) {
		return categoryDto.CategoryDetailRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/category/7", nil),
		map[string]string{"id": "7"})

	// The exception the service raised keeps its status all the way out.
	httpError(t, controller.Detail(c), http.StatusNotFound, exception.ErrNotFound.Message)
}

func TestUpdate(t *testing.T) {
	controller, service := newController()
	service.UpdateFn = func(_ context.Context, request categoryDto.CategoryUpdateReq) (categoryDto.CategoryUpdateRes, error) {
		return categoryDto.CategoryUpdateRes{Id: request.Id, Name: request.Name}, nil
	}

	c, recorder := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/category/7",
			`{"name":"  Elektronik Baru  ","description":"  Deskripsi  "}`),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Update(c))
	require.Equal(t, http.StatusOK, recorder.Code)

	// The identifier comes from the path, never from the body.
	require.Equal(t, []categoryDto.CategoryUpdateReq{{
		Id:          7,
		Name:        "Elektronik Baru",
		Description: "Deskripsi",
	}}, service.UpdateCalls)
}

func TestUpdateIgnoresTheIdentifierInTheBody(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/category/7", `{"id":99,"name":"Elektronik"}`),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Update(c))
	require.Equal(t, int64(7), service.UpdateCalls[0].Id)
}

func TestUpdateWithABodyThatIsNotJson(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(testutil.JSONRequest(http.MethodPut, "/api/category/7", `{`),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusBadRequest, "request body must be a valid json")
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/category/abc", `{"name":"Elektronik"}`),
		map[string]string{"id": "abc"})

	httpError(t, controller.Update(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Error())
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWithoutAName(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(testutil.JSONRequest(http.MethodPut, "/api/category/7", `{"name":"  "}`),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusBadRequest, "name is required")
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.UpdateFn = func(context.Context, categoryDto.CategoryUpdateReq) (categoryDto.CategoryUpdateRes, error) {
		return categoryDto.CategoryUpdateRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/category/7", `{"name":"Elektronik"}`),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusNotFound, exception.ErrNotFound.Message)
}

func TestActivate(t *testing.T) {
	controller, service := newController()
	service.ActivateFn = func(_ context.Context, request categoryDto.CategoryActivateReq) (categoryDto.CategoryActivateRes, error) {
		return categoryDto.CategoryActivateRes{Id: request.Id, Status: constant.Active}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/category/activate/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Activate(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []categoryDto.CategoryActivateReq{{Id: 7}}, service.ActivateCalls)
}

func TestActivateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/category/activate/abc", nil),
		map[string]string{"id": "abc"})

	httpError(t, controller.Activate(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.ActivateCalls)
}

func TestActivateAnAlreadyActiveCategory(t *testing.T) {
	controller, service := newController()
	service.ActivateFn = func(context.Context, categoryDto.CategoryActivateReq) (categoryDto.CategoryActivateRes, error) {
		return categoryDto.CategoryActivateRes{}, exception.ErrAlreadyActive
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/category/activate/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Activate(c), http.StatusConflict, exception.ErrAlreadyActive.Message)
}

func TestDeactivate(t *testing.T) {
	controller, service := newController()
	service.DeactivateFn = func(_ context.Context, request categoryDto.CategoryDeactivateReq) (categoryDto.CategoryDeactivateRes, error) {
		return categoryDto.CategoryDeactivateRes{Id: request.Id, Status: constant.Inactive}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/category/deactivate/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Deactivate(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []categoryDto.CategoryDeactivateReq{{Id: 7}}, service.DeactivateCalls)
}

func TestDeactivateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/category/deactivate/0", nil),
		map[string]string{"id": "0"})

	httpError(t, controller.Deactivate(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.DeactivateCalls)
}

func TestDeactivateAnAlreadyInactiveCategory(t *testing.T) {
	controller, service := newController()
	service.DeactivateFn = func(context.Context, categoryDto.CategoryDeactivateReq) (categoryDto.CategoryDeactivateRes, error) {
		return categoryDto.CategoryDeactivateRes{}, exception.ErrAlreadyInactive
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/category/deactivate/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Deactivate(c), http.StatusConflict, exception.ErrAlreadyInactive.Message)
}

func TestDelete(t *testing.T) {
	controller, service := newController()
	service.DeleteFn = func(_ context.Context, request categoryDto.CategoryDeleteReq) (categoryDto.CategoryDeleteRes, error) {
		return categoryDto.CategoryDeleteRes{Id: request.Id, Status: constant.Delete}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/category/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Delete(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []categoryDto.CategoryDeleteReq{{Id: 7}}, service.DeleteCalls)
}

func TestDeleteWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/category/-1", nil),
		map[string]string{"id": "-1"})

	httpError(t, controller.Delete(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.DeleteCalls)
}

func TestDeleteNotFound(t *testing.T) {
	controller, service := newController()
	service.DeleteFn = func(context.Context, categoryDto.CategoryDeleteReq) (categoryDto.CategoryDeleteRes, error) {
		return categoryDto.CategoryDeleteRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/category/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Delete(c), http.StatusNotFound, exception.ErrNotFound.Message)
}
