package product

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/base"
	productDto "product-service/internal/dto/product"
	"product-service/internal/exception"
	"product-service/internal/mocks"
	"product-service/internal/testutil"

	"github.com/labstack/echo/v5"
	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/require"
)

var errDatabase = errors.New("connection reset by peer")

func newController() (Controller, *mocks.ProductService) {
	service := &mocks.ProductService{}

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

// formFields is the payload of a complete product, padded so the trimming the
// handler does is visible.
func formFields() map[string]string {
	return map[string]string{
		"name":        "  Kipas Angin  ",
		"description": "  Kipas angin berdiri  ",
		"price":       "  199.99  ",
		"category_id": "  3  ",
		"supplier_id": "  4  ",
	}
}

func imagePart(filename string) *testutil.FilePart {
	return &testutil.FilePart{
		Field:       "image",
		Filename:    filename,
		ContentType: "image/png",
		Content:     []byte("fake image bytes"),
	}
}

// brokenMultipart is a request that claims to carry a multipart body but does
// not, so reading the form fails.
func brokenMultipart(method, target string) *http.Request {
	req := httptest.NewRequest(method, target, strings.NewReader("this is not a multipart body"))
	req.Header.Set(echo.HeaderContentType, "multipart/form-data; boundary=nonsense")

	return req
}

func TestCreate(t *testing.T) {
	controller, service := newController()
	service.CreateFn = func(_ context.Context, request productDto.ProductCreateReq) (productDto.ProductCreateRes, error) {
		return productDto.ProductCreateRes{Id: 11, Name: request.Name, Price: request.Price}, nil
	}

	c, recorder := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPost, "/api/product", formFields(), imagePart("kipas.png")), nil)

	require.NoError(t, controller.Create(c))
	require.Equal(t, http.StatusCreated, recorder.Code)

	response := body(t, recorder)
	require.Equal(t, http.StatusCreated, response.Status)
	require.Equal(t, constant.MsgSuccess, response.Message)
	require.Equal(t, "Kipas Angin", response.Data.(map[string]interface{})["name"])

	// Every field reaches the service trimmed and parsed, and the image is passed
	// on as the uploaded part.
	require.Len(t, service.CreateCalls, 1)
	request := service.CreateCalls[0]
	require.Equal(t, "Kipas Angin", request.Name)
	require.Equal(t, "Kipas angin berdiri", request.Description)
	require.True(t, decimal.RequireFromString("199.99").Equal(request.Price))
	require.Equal(t, int64(3), *request.CategoryId)
	require.Equal(t, int64(4), *request.SupplierId)
	require.NotNil(t, request.Image)
	require.Equal(t, "kipas.png", request.Image.Filename)
}

func TestCreateWithoutAnImage(t *testing.T) {
	controller, service := newController()

	c, recorder := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPost, "/api/product", formFields(), nil), nil)

	require.NoError(t, controller.Create(c))
	require.Equal(t, http.StatusCreated, recorder.Code)

	// A missing file is not an error, the product simply carries no image.
	require.Len(t, service.CreateCalls, 1)
	require.Nil(t, service.CreateCalls[0].Image)
}

func TestCreateWithABodyThatIsNotAForm(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(brokenMultipart(http.MethodPost, "/api/product"), nil)

	var echoError *echo.HTTPError
	require.ErrorAs(t, controller.Create(c), &echoError)
	require.Equal(t, http.StatusBadRequest, echoError.Code)
	require.Empty(t, service.CreateCalls)
}

func TestCreateWithFieldsThatAreNotNumbers(t *testing.T) {
	tests := []struct {
		field   string
		value   string
		wantErr string
	}{
		{field: "category_id", value: "abc", wantErr: "category_id must be a valid number"},
		{field: "supplier_id", value: "abc", wantErr: "supplier_id must be a valid number"},
		{field: "price", value: "abc", wantErr: "price must be a valid number"},
	}

	for _, test := range tests {
		t.Run(test.field, func(t *testing.T) {
			controller, service := newController()

			fields := formFields()
			fields[test.field] = test.value

			c, _ := testutil.NewContext(
				testutil.MultipartRequest(t, http.MethodPost, "/api/product", fields, nil), nil)

			httpError(t, controller.Create(c), http.StatusBadRequest, test.wantErr)
			require.Empty(t, service.CreateCalls)
		})
	}
}

func TestCreateWithABlankNumberField(t *testing.T) {
	controller, service := newController()

	// A blank optional number is no value at all rather than a parse failure, so
	// the payload is rejected by the validation instead.
	fields := formFields()
	fields["category_id"] = "   "

	c, _ := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPost, "/api/product", fields, nil), nil)

	httpError(t, controller.Create(c), http.StatusBadRequest, "category is required")
	require.Empty(t, service.CreateCalls)
}

func TestCreateWithAnIncompletePayload(t *testing.T) {
	tests := []struct {
		name    string
		field   string
		value   string
		wantErr string
	}{
		{name: "without a name", field: "name", value: "  ", wantErr: "name is required"},
		{name: "without a supplier", field: "supplier_id", value: "", wantErr: "supplier is required"},
		{name: "without a price", field: "price", value: "0", wantErr: "price is required"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			controller, service := newController()

			fields := formFields()
			fields[test.field] = test.value

			c, _ := testutil.NewContext(
				testutil.MultipartRequest(t, http.MethodPost, "/api/product", fields, nil), nil)

			httpError(t, controller.Create(c), http.StatusBadRequest, test.wantErr)
			require.Empty(t, service.CreateCalls)
		})
	}
}

func TestCreateWithAnUnsupportedImage(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPost, "/api/product", formFields(), imagePart("kipas.gif")), nil)

	httpError(t, controller.Create(c), http.StatusBadRequest,
		"image format must be one of jpg, jpeg, png, or webp")
	require.Empty(t, service.CreateCalls)
}

func TestCreateWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.CreateFn = func(context.Context, productDto.ProductCreateReq) (productDto.ProductCreateRes, error) {
		return productDto.ProductCreateRes{}, errDatabase
	}

	c, _ := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPost, "/api/product", formFields(), nil), nil)

	httpError(t, controller.Create(c), http.StatusInternalServerError, errDatabase.Error())
}

func TestSearch(t *testing.T) {
	controller, service := newController()
	service.SearchFn = func(_ context.Context, _ productDto.ProductSearchReq) (productDto.ProductSearchRes, error) {
		return productDto.ProductSearchRes{Data: []productDto.ProductDetailRes{{Id: 7, Name: "Kipas Angin"}}}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet,
		"/api/product?name=%20kipas%20&description=%20angin%20&min_price=10&max_price=500"+
			"&min_stock=1&max_stock=99&sort_by=price&sort_order=asc&page=2&page_size=5", nil), nil)

	require.NoError(t, controller.Search(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, constant.MsgSuccess, body(t, recorder).Message)

	require.Len(t, service.SearchCalls, 1)
	request := service.SearchCalls[0]
	require.Equal(t, "kipas", request.Name)
	require.Equal(t, "angin", request.Description)
	require.True(t, decimal.NewFromInt(10).Equal(request.MinPrice))
	require.True(t, decimal.NewFromInt(500).Equal(request.MaxPrice))
	require.Equal(t, 1, request.MinStock)
	require.Equal(t, 99, request.MaxStock)
	require.Equal(t, base.Paging{SortBy: "price", SortOrder: "asc", Page: 2, PageSize: 5}, request.Paging)
}

func TestSearchWithoutFilters(t *testing.T) {
	controller, service := newController()

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/product", nil), nil)

	require.NoError(t, controller.Search(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Len(t, service.SearchCalls, 1)
	require.True(t, service.SearchCalls[0].MinPrice.IsZero())
	require.Zero(t, service.SearchCalls[0].MinStock)
}

func TestSearchWithParametersThatAreNotNumbers(t *testing.T) {
	tests := []struct {
		param   string
		wantErr string
	}{
		{param: "min_price", wantErr: "min_price must be a valid number"},
		{param: "max_price", wantErr: "max_price must be a valid number"},
		{param: "min_stock", wantErr: "min_stock must be a valid number"},
		{param: "max_stock", wantErr: "max_stock must be a valid number"},
		{param: "page", wantErr: "page must be a valid number"},
		{param: "page_size", wantErr: "page_size must be a valid number"},
	}

	for _, test := range tests {
		t.Run(test.param, func(t *testing.T) {
			controller, service := newController()

			c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet,
				"/api/product?"+test.param+"=abc", nil), nil)

			httpError(t, controller.Search(c), http.StatusBadRequest, test.wantErr)
			require.Empty(t, service.SearchCalls)
		})
	}
}

func TestSearchWithAnInvalidFilter(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet,
		"/api/product?min_price=500&max_price=10", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest, "min_price must not be greater than max_price")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWithAnUnknownSortBy(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/product?sort_by=password", nil), nil)

	httpError(t, controller.Search(c), http.StatusBadRequest,
		"sort_by must be one of id, name, price, stock, created_at, or updated_at")
	require.Empty(t, service.SearchCalls)
}

func TestSearchWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.SearchFn = func(context.Context, productDto.ProductSearchReq) (productDto.ProductSearchRes, error) {
		return productDto.ProductSearchRes{}, errDatabase
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/product", nil), nil)

	httpError(t, controller.Search(c), http.StatusInternalServerError, errDatabase.Error())
}

func TestUpdate(t *testing.T) {
	controller, service := newController()
	service.UpdateFn = func(_ context.Context, request productDto.ProductUpdateReq) (productDto.ProductUpdateRes, error) {
		return productDto.ProductUpdateRes{Id: request.Id, Name: request.Name}, nil
	}

	c, recorder := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPut, "/api/product/7", formFields(), imagePart("kipas.png")),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Update(c))

	// The update endpoint answers 201, the same status its create counterpart uses.
	require.Equal(t, http.StatusCreated, recorder.Code)
	require.Equal(t, constant.MsgSuccess, body(t, recorder).Message)

	require.Len(t, service.UpdateCalls, 1)
	request := service.UpdateCalls[0]
	require.Equal(t, int64(7), request.Id)
	require.Equal(t, "Kipas Angin", request.Name)
	require.True(t, decimal.RequireFromString("199.99").Equal(request.Price))
	require.Equal(t, int64(3), *request.CategoryId)
	require.Equal(t, int64(4), *request.SupplierId)
	require.NotNil(t, request.Image)
}

func TestUpdateWithoutAnImage(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPut, "/api/product/7", formFields(), nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Update(c))
	require.Len(t, service.UpdateCalls, 1)
	require.Nil(t, service.UpdateCalls[0].Image)
}

func TestUpdateWithAnInvalidIdentifier(t *testing.T) {
	for _, id := range []string{"abc", "0", "-1"} {
		t.Run("id="+id, func(t *testing.T) {
			controller, service := newController()

			c, _ := testutil.NewContext(
				testutil.MultipartRequest(t, http.MethodPut, "/api/product/"+id, formFields(), nil),
				map[string]string{"id": id})

			httpError(t, controller.Update(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Error())
			require.Empty(t, service.UpdateCalls)
		})
	}
}

func TestUpdateWithABodyThatIsNotAForm(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(brokenMultipart(http.MethodPut, "/api/product/7"),
		map[string]string{"id": "7"})

	var echoError *echo.HTTPError
	require.ErrorAs(t, controller.Update(c), &echoError)
	require.Equal(t, http.StatusBadRequest, echoError.Code)
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWithFieldsThatAreNotNumbers(t *testing.T) {
	tests := []struct {
		field   string
		wantErr string
	}{
		{field: "category_id", wantErr: "category_id must be a valid number"},
		{field: "supplier_id", wantErr: "supplier_id must be a valid number"},
		{field: "price", wantErr: "price must be a valid number"},
	}

	for _, test := range tests {
		t.Run(test.field, func(t *testing.T) {
			controller, service := newController()

			fields := formFields()
			fields[test.field] = "abc"

			c, _ := testutil.NewContext(
				testutil.MultipartRequest(t, http.MethodPut, "/api/product/7", fields, nil),
				map[string]string{"id": "7"})

			httpError(t, controller.Update(c), http.StatusBadRequest, test.wantErr)
			require.Empty(t, service.UpdateCalls)
		})
	}
}

func TestUpdateWithAnIncompletePayload(t *testing.T) {
	tests := []struct {
		name    string
		field   string
		value   string
		wantErr string
	}{
		{name: "without a name", field: "name", value: "  ", wantErr: "name is required"},
		{name: "without a price", field: "price", value: "0", wantErr: "price is required"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			controller, service := newController()

			fields := formFields()
			fields[test.field] = test.value

			c, _ := testutil.NewContext(
				testutil.MultipartRequest(t, http.MethodPut, "/api/product/7", fields, nil),
				map[string]string{"id": "7"})

			httpError(t, controller.Update(c), http.StatusBadRequest, test.wantErr)
			require.Empty(t, service.UpdateCalls)
		})
	}
}

func TestUpdateWithAnUnsupportedImage(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPut, "/api/product/7", formFields(), imagePart("kipas.gif")),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusBadRequest,
		"image format must be one of jpg, jpeg, png, or webp")
	require.Empty(t, service.UpdateCalls)
}

func TestUpdateWhenTheServiceFails(t *testing.T) {
	controller, service := newController()
	service.UpdateFn = func(context.Context, productDto.ProductUpdateReq) (productDto.ProductUpdateRes, error) {
		return productDto.ProductUpdateRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(
		testutil.MultipartRequest(t, http.MethodPut, "/api/product/7", formFields(), nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Update(c), http.StatusNotFound, exception.ErrNotFound.Message)
}

func TestDetail(t *testing.T) {
	controller, service := newController()
	service.DetailFn = func(_ context.Context, request productDto.ProductDetailReq) (productDto.ProductDetailRes, error) {
		return productDto.ProductDetailRes{Id: request.Id, Name: "Kipas Angin"}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/product/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Detail(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, constant.MsgSuccess, body(t, recorder).Message)
	require.Equal(t, []productDto.ProductDetailReq{{Id: 7}}, service.DetailCalls)
}

func TestDetailWithAnInvalidIdentifier(t *testing.T) {
	for _, id := range []string{"abc", "0", "-1", ""} {
		t.Run("id="+id, func(t *testing.T) {
			controller, service := newController()

			c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/product/"+id, nil),
				map[string]string{"id": id})

			httpError(t, controller.Detail(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
			require.Empty(t, service.DetailCalls)
		})
	}
}

func TestDetailNotFound(t *testing.T) {
	controller, service := newController()
	service.DetailFn = func(context.Context, productDto.ProductDetailReq) (productDto.ProductDetailRes, error) {
		return productDto.ProductDetailRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodGet, "/api/product/7", nil),
		map[string]string{"id": "7"})

	// The exception the service raised keeps its status all the way out.
	httpError(t, controller.Detail(c), http.StatusNotFound, exception.ErrNotFound.Message)
}

func TestActivate(t *testing.T) {
	controller, service := newController()
	service.ActivateFn = func(_ context.Context, request productDto.ProductActivateReq) (productDto.ProductActivateRes, error) {
		return productDto.ProductActivateRes{Id: request.Id, Status: constant.Active}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/product/activate/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Activate(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []productDto.ProductActivateReq{{Id: 7}}, service.ActivateCalls)
}

func TestActivateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/product/activate/abc", nil),
		map[string]string{"id": "abc"})

	httpError(t, controller.Activate(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.ActivateCalls)
}

func TestActivateAnAlreadyActiveProduct(t *testing.T) {
	controller, service := newController()
	service.ActivateFn = func(context.Context, productDto.ProductActivateReq) (productDto.ProductActivateRes, error) {
		return productDto.ProductActivateRes{}, exception.ErrAlreadyActive
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/product/activate/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Activate(c), http.StatusConflict, exception.ErrAlreadyActive.Message)
}

func TestDeactivate(t *testing.T) {
	controller, service := newController()
	service.DeactivateFn = func(_ context.Context, request productDto.ProductDeactivateReq) (productDto.ProductDeactivateRes, error) {
		return productDto.ProductDeactivateRes{Id: request.Id, Status: constant.Inactive}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/product/deactivate/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Deactivate(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []productDto.ProductDeactivateReq{{Id: 7}}, service.DeactivateCalls)
}

func TestDeactivateWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/product/deactivate/0", nil),
		map[string]string{"id": "0"})

	httpError(t, controller.Deactivate(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.DeactivateCalls)
}

func TestDeactivateAnAlreadyInactiveProduct(t *testing.T) {
	controller, service := newController()
	service.DeactivateFn = func(context.Context, productDto.ProductDeactivateReq) (productDto.ProductDeactivateRes, error) {
		return productDto.ProductDeactivateRes{}, exception.ErrAlreadyInactive
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodPut, "/api/product/deactivate/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Deactivate(c), http.StatusConflict, exception.ErrAlreadyInactive.Message)
}

func TestDelete(t *testing.T) {
	controller, service := newController()
	service.DeleteFn = func(_ context.Context, request productDto.ProductDeleteReq) (productDto.ProductDeleteRes, error) {
		return productDto.ProductDeleteRes{Id: request.Id, Status: constant.Delete}, nil
	}

	c, recorder := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/product/7", nil),
		map[string]string{"id": "7"})

	require.NoError(t, controller.Delete(c))
	require.Equal(t, http.StatusOK, recorder.Code)
	require.Equal(t, []productDto.ProductDeleteReq{{Id: 7}}, service.DeleteCalls)
}

func TestDeleteWithAnInvalidIdentifier(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/product/-1", nil),
		map[string]string{"id": "-1"})

	httpError(t, controller.Delete(c), http.StatusBadRequest, exception.ErrInvalidIdentifier.Message)
	require.Empty(t, service.DeleteCalls)
}

func TestDeleteNotFound(t *testing.T) {
	controller, service := newController()
	service.DeleteFn = func(context.Context, productDto.ProductDeleteReq) (productDto.ProductDeleteRes, error) {
		return productDto.ProductDeleteRes{}, exception.ErrNotFound
	}

	c, _ := testutil.NewContext(httptest.NewRequest(http.MethodDelete, "/api/product/7", nil),
		map[string]string{"id": "7"})

	httpError(t, controller.Delete(c), http.StatusNotFound, exception.ErrNotFound.Message)
}

// urlencodedRequest builds a request whose body is a plain form rather than a
// multipart one, so the fields bind but the file part cannot be read.
func urlencodedRequest(method, target string, fields map[string]string) *http.Request {
	values := url.Values{}
	for name, value := range fields {
		values.Set(name, value)
	}

	req := httptest.NewRequest(method, target, strings.NewReader(values.Encode()))
	req.Header.Set(echo.HeaderContentType, echo.MIMEApplicationForm)

	return req
}

func TestCreateWithAFormThatCarriesNoFilePart(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(urlencodedRequest(http.MethodPost, "/api/product", formFields()), nil)

	// A plain form cannot hold a file, and that is a different failure from a
	// multipart request that simply left the image out.
	err := controller.Create(c)

	var echoError *echo.HTTPError
	require.ErrorAs(t, err, &echoError)
	require.Equal(t, http.StatusBadRequest, echoError.Code)
	require.Contains(t, echoError.Message, "isn't multipart/form-data")
	require.Empty(t, service.CreateCalls)
}

func TestUpdateWithAFormThatCarriesNoFilePart(t *testing.T) {
	controller, service := newController()

	c, _ := testutil.NewContext(urlencodedRequest(http.MethodPut, "/api/product/7", formFields()),
		map[string]string{"id": "7"})

	err := controller.Update(c)

	var echoError *echo.HTTPError
	require.ErrorAs(t, err, &echoError)
	require.Equal(t, http.StatusBadRequest, echoError.Code)
	require.Contains(t, echoError.Message, "isn't multipart/form-data")
	require.Empty(t, service.UpdateCalls)
}
