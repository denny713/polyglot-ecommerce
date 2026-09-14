package category

import (
	"net/http"
	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/category"
	"product-service/internal/exception"
	"product-service/internal/util"
	"strings"

	"github.com/labstack/echo/v5"
)

// Search godoc
// @Summary Search categories
// @Description Search categories by name and description (ILIKE).
// @Tags Category
// @Accept  json
// @Produce  json
// @Param name query string false "Category name, matched partially"
// @Param description query string false "Category description, matched partially"
// @Param sort_by query string false "Sort field" Enums(id, name, description, created_at, updated_at)
// @Param sort_order query string false "Sort direction" Enums(asc, desc)
// @Param page query integer false "Page number, starts at 1"
// @Param page_size query integer false "Rows per page, max 100"
// @Success 200 {object} dto.Response{data=category.CategorySearchRes}
// @Failure 400 {object} dto.Response
// @Failure 401 {object} dto.Response
// @Failure 403 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Security BearerAuth
// @Router /api/category [get]
func (ctrl Controller) Search(c *echo.Context) error {
	request, err := bindCategorySearchReq(c)
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

// bindCategorySearchReq reads the filters from the query string, every parameter
// is optional so a missing one is left at its zero value.
func bindCategorySearchReq(c *echo.Context) (category.CategorySearchReq, error) {
	var request category.CategorySearchReq

	request.Name = strings.TrimSpace(c.QueryParam("name"))
	request.Description = strings.TrimSpace(c.QueryParam("description"))
	request.SortBy = strings.TrimSpace(c.QueryParam("sort_by"))
	request.SortOrder = strings.TrimSpace(c.QueryParam("sort_order"))
	page, err := util.IntQueryParam(c, "page")
	if err != nil {
		return request, err
	}

	pageSize, err := util.IntQueryParam(c, "page_size")
	if err != nil {
		return request, err
	}

	request.Page = page
	request.PageSize = pageSize

	return request, nil
}
