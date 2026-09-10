package supplier

import (
	"net/http"
	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/supplier"
	"product-service/internal/exception"
	"strconv"

	"github.com/labstack/echo/v5"
)

// Delete godoc
// @Summary Delete a supplier
// @Description Delete a specific supplier.
// @Tags Supplier
// @Accept  json
// @Produce  json
// @Param id path string true "Supplier ID"
// @Success 200 {object} dto.Response{data=supplier.SupplierDeleteRes}
// @Failure 400 {object} dto.Response
// @Failure 401 {object} dto.Response
// @Failure 403 {object} dto.Response
// @Failure 404 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Security BearerAuth
// @Router /api/supplier/{id} [delete]
func (ctrl Controller) Delete(c *echo.Context) error {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return exception.HTTPError(exception.ErrInvalidIdentifier)
	}

	response, err := ctrl.service.Delete(c.Request().Context(), supplier.SupplierDeleteReq{Id: id})
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
