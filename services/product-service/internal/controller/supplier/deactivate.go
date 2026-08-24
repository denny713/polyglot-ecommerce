package supplier

import (
	"net/http"
	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/supplier"
	"product-service/internal/exception"
	service "product-service/internal/service/supplier"
	"strconv"

	"github.com/labstack/echo/v5"
)

// Deactivate godoc
// @Summary Deactivate a supplier
// @Description Deactivate a specific supplier.
// @Tags Supplier
// @Accept  json
// @Produce  json
// @Param id path string true "Supplier ID"
// @Success 200 {object} dto.Response{data=supplier.SupplierDeactivateRes}
// @Failure 400 {object} dto.Response
// @Failure 404 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/supplier/deactivate/{id} [put]
func Deactivate(c *echo.Context) error {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return exception.HTTPError(exception.ErrInvalidIdentifier)
	}

	response, err := service.Deactivate(c.Request().Context(), supplier.SupplierDeactivateReq{Id: id})
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
