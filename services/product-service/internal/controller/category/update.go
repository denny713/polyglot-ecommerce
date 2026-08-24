package category

import (
	"net/http"
	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/category"
	"product-service/internal/exception"
	service "product-service/internal/service/category"
	"strconv"
	"strings"

	"github.com/labstack/echo/v5"
)

// Update godoc
// @Summary Update an existing category
// @Description Update an existing category, the payload is sent as a json body.
// @Tags Category
// @Accept  json
// @Produce  json
// @Param id path string true "Category ID"
// @Param request body category.CategoryUpdateReq true "Category payload"
// @Success 200 {object} dto.Response{data=category.CategoryUpdateRes}
// @Failure 400 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/category/{id} [put]
func Update(c *echo.Context) error {
	var request category.CategoryUpdateReq
	if err := c.Bind(&request); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, "request body must be a valid json")
	}

	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return echo.NewHTTPError(http.StatusBadRequest, exception.ErrInvalidIdentifier.Error())
	}

	request.Id = id
	request.Name = strings.TrimSpace(request.Name)
	request.Description = strings.TrimSpace(request.Description)

	if err := request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := service.Update(c.Request().Context(), request)
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
