package category

import (
	"net/http"
	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/category"
	"product-service/internal/exception"
	"strconv"

	"github.com/labstack/echo/v5"
)

// Deactivate godoc
// @Summary Deactivate a category
// @Description Deactivate a specific category.
// @Tags Category
// @Accept json
// @Produce json
// @Param id path string true "Category ID"
// @Success 200 {object} dto.Response{data=category.CategoryDeactivateRes}
// @Failure 400 {object} dto.Response
// @Failure 404 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/category/deactivate/{id} [put]
func (ctrl Controller) Deactivate(c *echo.Context) error {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return exception.HTTPError(exception.ErrInvalidIdentifier)
	}

	response, err := ctrl.service.Deactivate(c.Request().Context(), category.CategoryDeactivateReq{Id: id})
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
