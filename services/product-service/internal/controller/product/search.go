package product

import (
	"net/http"
	"strings"

	"product-service/internal/constant"
	dto "product-service/internal/dto"
	product "product-service/internal/dto/product"
	exception "product-service/internal/exception"
	"product-service/internal/util"

	"github.com/labstack/echo/v5"
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
func (ctrl Controller) Search(c *echo.Context) error {
	request, err := bindProductSearchReq(c)
	if err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	if err = request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := ctrl.service.Search(c.Request().Context(), request)
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

	minPrice, err := util.DecimalQueryParam(c, "min_price")
	if err != nil {
		return request, err
	}

	maxPrice, err := util.DecimalQueryParam(c, "max_price")
	if err != nil {
		return request, err
	}

	minStock, err := util.IntQueryParam(c, "min_stock")
	if err != nil {
		return request, err
	}

	maxStock, err := util.IntQueryParam(c, "max_stock")
	if err != nil {
		return request, err
	}

	page, err := util.IntQueryParam(c, "page")
	if err != nil {
		return request, err
	}

	pageSize, err := util.IntQueryParam(c, "page_size")
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
