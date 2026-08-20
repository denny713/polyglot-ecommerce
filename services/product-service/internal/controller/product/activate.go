package product

import (
	"errors"
	"net/http"
	"strconv"

	dto "product-service/internal/dto"
	product "product-service/internal/dto/product"
	service "product-service/internal/service/product"

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
func Activate(c *echo.Context) error {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return echo.NewHTTPError(http.StatusBadRequest, "product ID must be a valid number")
	}

	response, err := service.Activate(c.Request().Context(), product.ProductActivateReq{ID: id})
	if err != nil {
		if errors.Is(err, service.ErrProductNotFound) {
			return echo.NewHTTPError(http.StatusNotFound, err.Error())
		}

		return echo.NewHTTPError(http.StatusInternalServerError, err.Error())
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: "product details retrieved successfully",
		Data:    response,
	})
}
