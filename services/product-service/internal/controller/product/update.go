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

	"github.com/labstack/echo/v5"
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
// @Param buy_price formData number true "Product buying price from the supplier"
// @Param sell_price formData number true "Product selling price to the customer"
// @Param category_id formData number true "Product category"
// @Param supplier_id formData number true "Product supplier"
// @Param image formData file false "Product image (jpg, jpeg, png, webp, max 5 MB)"
// @Success 201 {object} dto.Response{data=product.ProductUpdateRes}
// @Failure 400 {object} dto.Response
// @Failure 401 {object} dto.Response
// @Failure 403 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Security BearerAuth
// @Router /api/product/{id} [put]
func (ctrl Controller) Update(c *echo.Context) error {
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

	response, err := ctrl.service.Update(c.Request().Context(), request)
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
		return request, exception.ErrInvalidIdentifier
	}

	if _, err := c.FormValues(); err != nil {
		return request, err
	}

	request.Id = id
	request.Name = strings.TrimSpace(c.FormValue("name"))
	request.Description = strings.TrimSpace(c.FormValue("description"))

	if categoryId := strings.TrimSpace(c.FormValue("category_id")); categoryId != "" {
		parsedCategoryId, err := strconv.ParseInt(categoryId, 10, 64)
		if err != nil {
			return request, errors.New("category_id must be a valid number")
		}

		request.CategoryId = &parsedCategoryId
	}

	if supplierId := strings.TrimSpace(c.FormValue("supplier_id")); supplierId != "" {
		parsedSupplierId, err := strconv.ParseInt(supplierId, 10, 64)
		if err != nil {
			return request, errors.New("supplier_id must be a valid number")
		}

		request.SupplierId = &parsedSupplierId
	}

	buyPrice, err := decimalFormValue(c, "buy_price")
	if err != nil {
		return request, err
	}

	sellPrice, err := decimalFormValue(c, "sell_price")
	if err != nil {
		return request, err
	}

	request.BuyPrice = buyPrice
	request.SellPrice = sellPrice

	image, err := c.FormFile("image")
	if err != nil && !errors.Is(err, http.ErrMissingFile) {
		return request, err
	}

	request.Image = image

	return request, nil
}
