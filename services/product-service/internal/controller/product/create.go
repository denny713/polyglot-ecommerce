package product

import (
	"errors"
	"net/http"
	"strings"

	"product-service/internal/dto"
	dtoProduct "product-service/internal/dto/product"
	service "product-service/internal/service/product"

	"github.com/labstack/echo/v5"
	"github.com/shopspring/decimal"
)

// Create godoc
// @Summary Create a new product
// @Description Create a new product, the image is uploaded to the object storage.
// @Tags Product
// @Accept  multipart/form-data
// @Produce  json
// @Param name formData string true "Product name"
// @Param description formData string false "Product description"
// @Param price formData number true "Product price"
// @Param image formData file false "Product image (jpg, jpeg, png, webp, max 5 MB)"
// @Success 201 {object} dto.Response{data=product.ProductCreateRes}
// @Failure 400 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/product [post]
func Create(c *echo.Context) error {
	request, err := bindProductCreateReq(c)
	if err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	if err = request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	if err = request.ValidateImage(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := service.Create(c.Request().Context(), request)
	if err != nil {
		return echo.NewHTTPError(http.StatusInternalServerError, err.Error())
	}

	return c.JSON(http.StatusCreated, dto.Response{
		Status:  http.StatusCreated,
		Message: "product created successfully",
		Data:    response,
	})
}

// bindProductCreateReq reads the multipart form, the request carries a file so it
// cannot be filled by the default json binder.
func bindProductCreateReq(c *echo.Context) (dtoProduct.ProductCreateReq, error) {
	var request dtoProduct.ProductCreateReq

	if _, err := c.FormValues(); err != nil {
		return request, err
	}

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
