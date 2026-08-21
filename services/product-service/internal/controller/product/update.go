package product

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"product-service/internal/constant"
	"product-service/internal/dto"
	product "product-service/internal/dto/product"
	exception "product-service/internal/exception"
	service "product-service/internal/service/product"

	"github.com/labstack/echo/v5"
	"github.com/shopspring/decimal"
)

// Update godoc
// @Summary Update an existing product
// @Description Update an existing product, the image is uploaded to the object storage.
// @Tags Product
// @Accept  multipart/form-data
// @Produce  json
// @Param id path string true "Product ID"
// @Param name formData string true "Product name"
// @Param description formData string false "Product description"
// @Param price formData number true "Product price"
// @Param image formData file false "Product image (jpg, jpeg, png, webp, max 5 MB)"
// @Success 201 {object} dto.Response{data=product.ProductUpdateRes}
// @Failure 400 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/product/{id} [put]
func Update(c *echo.Context) error {
	request, err := bindProductUpdateReq(c)
	if err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	if err = request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	if err = request.ValidateImage(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := service.Update(c.Request().Context(), request)
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusCreated, dto.Response{
		Status:  http.StatusCreated,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}

// bindProductUpdateReq reads the multipart form, the request carries a file so it
// cannot be filled by the default json binder.
func bindProductUpdateReq(c *echo.Context) (product.ProductUpdateReq, error) {
	var request product.ProductUpdateReq

	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return request, exception.ErrProductIDInvalid
	}

	if _, err := c.FormValues(); err != nil {
		return request, err
	}

	request.Id = id
	request.Name = strings.TrimSpace(c.FormValue("name"))
	request.Description = strings.TrimSpace(c.FormValue("description"))

	if price := strings.TrimSpace(c.FormValue("price")); price != "" {
		parsedPrice, err := decimal.NewFromString(price)
		if err != nil {
			return request, errors.New("price must be a valid number")
		}

		request.Price = parsedPrice
	}

	image, err := c.FormFile("image")
	if err != nil && !errors.Is(err, http.ErrMissingFile) {
		return request, err
	}

	request.Image = image

	return request, nil
}
