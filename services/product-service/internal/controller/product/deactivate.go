package product

import (
	"net/http"
	"strconv"

	"product-service/internal/constant"
	dto "product-service/internal/dto"
	product "product-service/internal/dto/product"
	exception "product-service/internal/exception"
	service "product-service/internal/service/product"

	"github.com/labstack/echo/v5"
)

// Deactivate godoc
// @Summary Deactivate a product
// @Description Deactivate a specific product.
// @Tags Product
// @Accept  json
// @Produce  json
// @Param id path string true "Product ID"
// @Success 200 {object} dto.Response{data=product.ProductDeactivateRes}
// @Failure 400 {object} dto.Response
// @Failure 404 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/product/deactivate/{id} [put]
func Deactivate(c *echo.Context) error {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return exception.HTTPError(exception.ErrProductIDInvalid)
	}

	response, err := service.Deactivate(c.Request().Context(), product.ProductDeactivateReq{ID: id})
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
