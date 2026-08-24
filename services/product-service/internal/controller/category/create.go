package category

import (
	"net/http"
	"strings"

	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/category"
	"product-service/internal/exception"
	service "product-service/internal/service/category"

	"github.com/labstack/echo/v5"
)

// Create godoc
// @Summary Create a new category
// @Description Create a new category, the payload is sent as a json body.
// @Tags Category
// @Accept  json
// @Produce  json
// @Param request body category.CategoryCreateReq true "Category payload"
// @Success 201 {object} dto.Response{data=category.CategoryCreateRes}
// @Failure 400 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/category [post]
func Create(c *echo.Context) error {
	var request category.CategoryCreateReq
	if err := c.Bind(&request); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, "request body must be a valid json")
	}

	request.Name = strings.TrimSpace(request.Name)
	request.Description = strings.TrimSpace(request.Description)

	if err := request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := service.Create(c.Request().Context(), request)
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusCreated, dto.Response{
		Status:  http.StatusCreated,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
