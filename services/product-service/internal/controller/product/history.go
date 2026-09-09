package product

import (
	"net/http"
	"product-service/internal/constant"
	dto "product-service/internal/dto"
	product "product-service/internal/dto/product"
	"product-service/internal/exception"
	"strconv"

	"github.com/labstack/echo/v5"
)

// History godoc
// @Summary Detail product stock history
// @Description Get product stock history.
// @Tags Product
// @Accept  json
// @Produce  json
// @Param id path string true "Product ID"
// @Success 200 {object} dto.Response{data=product.ProductHistoryRes}
// @Failure 400 {object} dto.Response
// @Failure 404 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/product/history/{id} [get]
func (ctrl Controller) History(c *echo.Context) error {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return exception.HTTPError(exception.ErrInvalidIdentifier)
	}

	response, err := ctrl.service.History(c.Request().Context(), product.ProductHistoryReq{Id: id})
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
