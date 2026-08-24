package supplier

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
	supplierDto "product-service/internal/dto/supplier"
	"product-service/internal/exception"
	"product-service/internal/mocks"
	"product-service/internal/testutil"

	"github.com/labstack/echo/v5"
	"github.com/stretchr/testify/require"
)

var errDatabase = errors.New("connection reset by peer")

func newController() (Controller, *mocks.SupplierService) {
	service := &mocks.SupplierService{}

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
	service.CreateFn = func(_ context.Context, request supplierDto.SupplierCreateReq) (supplierDto.SupplierCreateRes, error) {
		return supplierDto.SupplierCreateRes{Id: 11, Name: request.Name, IsActive: true}, nil
	}

	// The payload is padded on purpose, the handler trims every field of it before
	// it validates.
	c, recorder := testutil.NewContext(
		testutil.JSONRequest(http.MethodPost, "/api/supplier", `{
			"name":"  PT Maju  ","phone":"  0211234567  ","email":"  sales@maju.test  ",
			"contact_person":"  Budi  ","address":"  Jl. Merdeka 1  ","province":"  DKI Jakarta  ",
			"city":"  Jakarta Pusat  ","district":"  Gambir  ","subdistrict":"  Petojo  ",
			"postal_code":"  10110  ","note":"  Pemasok utama  "}`), nil)

	require.NoError(t, controller.Create(c))
	require.Equal(t, http.StatusCreated, recorder.Code)

	response := body(t, recorder)
	require.Equal(t, http.StatusCreated, response.Status)
	require.Equal(t, constant.MsgSuccess, response.Message)
	require.Equal(t, "PT Maju", response.Data.(map[string]interface{})["name"])

	require.Equal(t, []supplierDto.SupplierCreateReq{{
		Name:          "PT Maju",
		Phone:         "0211234567",
		Email:         "sales@maju.test",
		ContactPerson: "Budi",
		Address:       "Jl. Merdeka 1",
		Province:      "DKI Jakarta",
		City:          "Jakarta Pusat",
		District:      "Gambir",
		Subdistrict:   "Petojo",
		PostalCode:    "10110",
		Note:          "Pemasok utama",
	}}, service.CreateCalls)
}

func TestCreateWithABodyThatIsNotJson(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(testutil.JSONRequest(http.MethodPost, "/api/supplier", `{`), nil)

	httpError(t, controller.Create(c), http.StatusBadRequest, "request body must be a valid json")
	require.Empty(t, service.CreateCalls)
}

func TestCreateWithoutAName(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPost, "/api/supplier", `{"name":"   "}`), nil)

	httpError(t, controller.Create(c), http.StatusBadRequest, "name is required")
	require.Empty(t, service.CreateCalls)
}

func TestCreateWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.CreateFn = func(context.Context, supplierDto.SupplierCreateReq) (supplierDto.SupplierCreateRes, error) {
		return supplierDto.SupplierCreateRes{}, errDatabase
	}

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPost, "/api/supplier", `{"name":"PT Maju"}`), nil)

	// An error that is not an exception is reported as an internal failure.
	httpError(t, controller.Create(c), http.StatusInternalServerError, errDatabase.Error())
}

func TestSearch(t *testing.T) {
	controller, service := newController()
	service.SearchFn = func(_ context.Context, _ supplierDto.SupplierSearchReq) (supplierDto.SupplierSearchRes, error) {
		return supplierDto.SupplierSearchRes{Data: []supplierDto.SupplierDetailRes{{Id: 7, Name: "PT Maju"}}}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet,
		"/api/supplier?name=%20maju%20&phone=%20021%20&email=%20sales%20&contact_person=%20budi%20"+
			"&province=%20dki%20&city=%20jakarta%20&district=%20gambir%20&subdistrict=%20petojo%20"+
			"&postal_code=%2010110%20&sort_by=city&sort_order=asc&page=2&page_size=5", nil), nil)

	require.NoError(t, controller.Search(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, constant.MsgSuccess, body(t, recorder).Message)

	// Every filter reaches the service trimmed.
	require.Equal(t, []supplierDto.SupplierSearchReq{{
		Name:          "maju",
		Phone:         "021",
		Email:         "sales",
		ContactPerson: "budi",
		Province:      "dki",
		City:          "jakarta",
		District:      "gambir",
		Subdistrict:   "petojo",
		PostalCode:    "10110",
		Paging:        base.Paging{SortBy: "city", SortOrder: "asc", Page: 2, PageSize: 5},
	}}, service.SearchCalls)
}

func TestSearchWithoutFilters(t *testing.T) {
	controller, service := newController()

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier", nil), nil)

	require.NoError(t, controller.Search(c))
	require.Equal(t, http.StatusOK, recorder.Code)

	// A missing parameter is left at its zero value, the service fills the
	// defaults in.
	require.Equal(t, []supplierDto.SupplierSearchReq{{}}, service.SearchCalls)
}

func TestSearchWithAPageThatIsNotANumber(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier?page=abc", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest, "page must be a valid number")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWithAPageSizeThatIsNotANumber(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier?page_size=abc", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest, "page_size must be a valid number")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWithAnUnknownSortBy(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier?sort_by=password", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest,
		"sort_by must be one of id, name, email, contact_person, province, city, created_at, or updated_at")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.SearchFn = func(context.Context, supplierDto.SupplierSearchReq) (supplierDto.SupplierSearchRes, error) {
		return supplierDto.SupplierSearchRes{}, errDatabase
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier", nil), nil)

	httpError(t, controller.Search(c), http.StatusInternalServerError, errDatabase.Error())
}

func TestDetail(t *testing.T) {
	controller, service := newController()
	service.DetailFn = func(_ context.Context, request supplierDto.SupplierDetailReq) (supplierDto.SupplierDetailRes, error) {
		return supplierDto.SupplierDetailRes{Id: request.Id, Name: "PT Maju"}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Detail(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, constant.MsgSuccess, body(t, recorder).Message)
	require.Equal(t, []supplierDto.SupplierDetailReq{{Id: 7}}, service.DetailCalls)
}

func TestDetailWithAnInvalidIdentifier(t *testing.T) {
	for _, id := range []string{"abc", "0", "-1", ""} {
		t.Run("id="+id, func(t *testing.T) {
			controller, service := newController()

			c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier/"+id, nil),
				map[string]string{"id": id})

			httpError(t, controller.Detail(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
			require.Empty(t, service.DetailCalls)
		})
	}
}

func TestDetailNotFound(t *testing.T) {
	controller, service := newController()
	service.DetailFn = func(context.Context, supplierDto.SupplierDetailReq) (supplierDto.SupplierDetailRes, error) {
		return supplierDto.SupplierDetailRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/supplier/7", nil),
		map[string]string{"id": "7"})

	// The exception the service raised keeps its status all the way out.
	httpError(t, controller.Detail(c), http.StatusNotFound, exception.ErrNotFound.Message)
}

func TestUpdate(t *testing.T) {
	controller, service := newController()
	service.UpdateFn = func(_ context.Context, request supplierDto.SupplierUpdateReq) (supplierDto.SupplierUpdateRes, error) {
		return supplierDto.SupplierUpdateRes{Id: request.Id, Name: request.Name}, nil
	}

	c, recorder := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/supplier/7", `{
			"name":"  PT Maju Jaya  ","phone":"  0217654321  ","email":"  info@maju.test  ",
			"contact_person":"  Sari  ","address":"  Jl. Merdeka 2  ","province":"  Jawa Barat  ",
			"city":"  Bandung  ","district":"  Coblong  ","subdistrict":"  Dago  ",
			"postal_code":"  40135  ","note":"  Alamat baru  "}`),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Update(c))
	require.Equal(t, http.StatusOK, recorder.Code)

	// The identifier comes from the path, never from the body.
	require.Equal(t, []supplierDto.SupplierUpdateReq{{
		Id:            7,
		Name:          "PT Maju Jaya",
		Phone:         "0217654321",
		Email:         "info@maju.test",
		ContactPerson: "Sari",
		Address:       "Jl. Merdeka 2",
		Province:      "Jawa Barat",
		City:          "Bandung",
		District:      "Coblong",
		Subdistrict:   "Dago",
		PostalCode:    "40135",
		Note:          "Alamat baru",
	}}, service.UpdateCalls)
}

func TestUpdateIgnoresTheIdentifierInTheBody(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/supplier/7", `{"id":99,"name":"PT Maju"}`),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Update(c))
	require.Equal(t, int64(7), service.UpdateCalls[0].Id)
}

func TestUpdateWithABodyThatIsNotJson(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(testutil.JSONRequest(http.MethodPut, "/api/supplier/7", `{`),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusBadRequest, "request body must be a valid json")
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/supplier/abc", `{"name":"PT Maju"}`),
		map[string]string{"id": "abc"})

	httpError(t, controller.Update(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Error())
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWithoutAName(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(testutil.JSONRequest(http.MethodPut, "/api/supplier/7", `{"name":"  "}`),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusBadRequest, "name is required")
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.UpdateFn = func(context.Context, supplierDto.SupplierUpdateReq) (supplierDto.SupplierUpdateRes, error) {
		return supplierDto.SupplierUpdateRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(
		testutil.JSONRequest(http.MethodPut, "/api/supplier/7", `{"name":"PT Maju"}`),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusNotFound, exception.ErrNotFound.Message)
}

func TestActivate(t *testing.T) {
	controller, service := newController()
	service.ActivateFn = func(_ context.Context, request supplierDto.SupplierActivateReq) (supplierDto.SupplierActivateRes, error) {
		return supplierDto.SupplierActivateRes{Id: request.Id, Status: constant.Active}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/supplier/activate/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Activate(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []supplierDto.SupplierActivateReq{{Id: 7}}, service.ActivateCalls)
}

func TestActivateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/supplier/activate/abc", nil),
		map[string]string{"id": "abc"})

	httpError(t, controller.Activate(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.ActivateCalls)
}

func TestActivateAnAlreadyActiveSupplier(t *testing.T) {
	controller, service := newController()
	service.ActivateFn = func(context.Context, supplierDto.SupplierActivateReq) (supplierDto.SupplierActivateRes, error) {
		return supplierDto.SupplierActivateRes{}, exception.ErrAlreadyActive
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/supplier/activate/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Activate(c), http.StatusConflict, exception.ErrAlreadyActive.Message)
}

func TestDeactivate(t *testing.T) {
	controller, service := newController()
	service.DeactivateFn = func(_ context.Context, request supplierDto.SupplierDeactivateReq) (supplierDto.SupplierDeactivateRes, error) {
		return supplierDto.SupplierDeactivateRes{Id: request.Id, Status: constant.Inactive}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/supplier/deactivate/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Deactivate(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []supplierDto.SupplierDeactivateReq{{Id: 7}}, service.DeactivateCalls)
}

func TestDeactivateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/supplier/deactivate/0", nil),
		map[string]string{"id": "0"})

	httpError(t, controller.Deactivate(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.DeactivateCalls)
}

func TestDeactivateAnAlreadyInactiveSupplier(t *testing.T) {
	controller, service := newController()
	service.DeactivateFn = func(context.Context, supplierDto.SupplierDeactivateReq) (supplierDto.SupplierDeactivateRes, error) {
		return supplierDto.SupplierDeactivateRes{}, exception.ErrAlreadyInactive
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/supplier/deactivate/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Deactivate(c), http.StatusConflict, exception.ErrAlreadyInactive.Message)
}

func TestDelete(t *testing.T) {
	controller, service := newController()
	service.DeleteFn = func(_ context.Context, request supplierDto.SupplierDeleteReq) (supplierDto.SupplierDeleteRes, error) {
		return supplierDto.SupplierDeleteRes{Id: request.Id, Status: constant.Delete}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/supplier/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Delete(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []supplierDto.SupplierDeleteReq{{Id: 7}}, service.DeleteCalls)
}

func TestDeleteWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/supplier/-1", nil),
		map[string]string{"id": "-1"})

	httpError(t, controller.Delete(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.DeleteCalls)
}

func TestDeleteNotFound(t *testing.T) {
	controller, service := newController()
	service.DeleteFn = func(context.Context, supplierDto.SupplierDeleteReq) (supplierDto.SupplierDeleteRes, error) {
		return supplierDto.SupplierDeleteRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/supplier/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Delete(c), http.StatusNotFound, exception.ErrNotFound.Message)
}
