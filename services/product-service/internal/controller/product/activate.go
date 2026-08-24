package product

import (
	"net/http"
	"strconv"

	"product-service/internal/constant"
	dto "product-service/internal/dto"
	product "product-service/internal/dto/product"
	"product-service/internal/exception"

	"github.com/labstack/echo/v5"
)

// Activate godoc
// @Summary Activate a product
// @Description Activate a specific product.
// @Tags Product
// @Accept  json
// @Produce  json
// @Param id path string true "Product ID"
// @Success 200 {object} dto.Response{data=product.ProductActivateRes}
// @Failure 400 {object} dto.Response
// @Failure 404 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/product/activate/{id} [put]
func (ctrl Controller) Activate(c *echo.Context) error {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return exception.HTTPError(exception.ErrInvalidIdentifier)
	}

	response, err := ctrl.service.Activate(c.Request().Context(), product.ProductActivateReq{Id: id})
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
