package product

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"product-service/internal/constant"
	dto "product-service/internal/dto"
	product "product-service/internal/dto/product"
	exception "product-service/internal/exception"
	service "product-service/internal/service/product"

	"github.com/labstack/echo/v5"
	"github.com/shopspring/decimal"
)

// Search godoc
// @Summary Search products
// @Description Search products by name and description (ILIKE), price range and stock range.
// @Tags Product
// @Accept  json
// @Produce  json
// @Param name query string false "Product name, matched partially"
// @Param description query string false "Product description, matched partially"
// @Param min_price query number false "Minimum product price"
// @Param max_price query number false "Maximum product price"
// @Param min_stock query integer false "Minimum stock quantity"
// @Param max_stock query integer false "Maximum stock quantity"
// @Param sort_by query string false "Sort field" Enums(id, name, price, stock, created_at, updated_at)
// @Param sort_order query string false "Sort direction" Enums(asc, desc)
// @Param page query integer false "Page number, starts at 1"
// @Param page_size query integer false "Rows per page, max 100"
// @Success 200 {object} dto.Response{data=product.ProductSearchRes}
// @Failure 400 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/product [get]
func Search(c *echo.Context) error {
	request, err := bindProductSearchReq(c)
	if err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	if err = request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := service.Search(c.Request().Context(), request)
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}

// bindProductSearchReq reads the filters from the query string, every parameter
// is optional so a missing one is left at its zero value.
func bindProductSearchReq(c *echo.Context) (product.ProductSearchReq, error) {
	var request product.ProductSearchReq

	request.Name = strings.TrimSpace(c.QueryParam("name"))
	request.Description = strings.TrimSpace(c.QueryParam("description"))
	request.SortBy = strings.TrimSpace(c.QueryParam("sort_by"))
	request.SortOrder = strings.TrimSpace(c.QueryParam("sort_order"))

	minPrice, err := decimalQueryParam(c, "min_price")
	if err != nil {
		return request, err
	}

	maxPrice, err := decimalQueryParam(c, "max_price")
	if err != nil {
		return request, err
	}

	minStock, err := intQueryParam(c, "min_stock")
	if err != nil {
		return request, err
	}

	maxStock, err := intQueryParam(c, "max_stock")
	if err != nil {
		return request, err
	}

	page, err := intQueryParam(c, "page")
	if err != nil {
		return request, err
	}

	pageSize, err := intQueryParam(c, "page_size")
	if err != nil {
		return request, err
	}

	request.MinPrice = minPrice
	request.MaxPrice = maxPrice
	request.MinStock = minStock
	request.MaxStock = maxStock
	request.Page = page
	request.PageSize = pageSize

	return request, nil
}

// decimalQueryParam parses a decimal query parameter, an empty one is zero.
func decimalQueryParam(c *echo.Context, name string) (decimal.Decimal, error) {
	value := strings.TrimSpace(c.QueryParam(name))
	if value == "" {
		return decimal.Zero, nil
	}

	parsed, err := decimal.NewFromString(value)
	if err != nil {
		return decimal.Zero, errors.New(name + " must be a valid number")
	}

	return parsed, nil
}

// intQueryParam parses an integer query parameter, an empty one is zero.
func intQueryParam(c *echo.Context, name string) (int, error) {
	value := strings.TrimSpace(c.QueryParam(name))
	if value == "" {
		return 0, nil
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, errors.New(name + " must be a valid number")
	}

	return parsed, nil
}
