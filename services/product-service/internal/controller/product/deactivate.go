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
		return echo.NewHTTPError(http.StatusBadRequest, "product ID must be a valid number")
	}

	response, err := service.Deactivate(c.Request().Context(), product.ProductDeactivateReq{ID: id})
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
