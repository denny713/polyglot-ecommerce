package supplier

import (
	"net/http"
	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/supplier"
	"product-service/internal/exception"
	"strconv"
	"strings"

	"github.com/labstack/echo/v5"
)

// Update godoc
// @Summary Update an existing supplier
// @Description Update an existing supplier, the payload is sent as a json body.
// @Tags Supplier
// @Accept  json
// @Produce  json
// @Param id path string true "Supplier ID"
// @Param request body supplier.SupplierUpdateReq true "Supplier payload"
// @Success 200 {object} dto.Response{data=supplier.SupplierUpdateRes}
// @Failure 400 {object} dto.Response
// @Failure 404 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/supplier/{id} [put]
func (ctrl Controller) Update(c *echo.Context) error {
	var request supplier.SupplierUpdateReq
	if err := c.Bind(&request); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, "request body must be a valid json")
	}

	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		return echo.NewHTTPError(http.StatusBadRequest, exception.ErrInvalidIdentifier.Error())
	}

	request.Id = id
	request.Name = strings.TrimSpace(request.Name)
	request.Phone = strings.TrimSpace(request.Phone)
	request.Email = strings.TrimSpace(request.Email)
	request.ContactPerson = strings.TrimSpace(request.ContactPerson)
	request.Address = strings.TrimSpace(request.Address)
	request.Province = strings.TrimSpace(request.Province)
	request.City = strings.TrimSpace(request.City)
	request.District = strings.TrimSpace(request.District)
	request.Subdistrict = strings.TrimSpace(request.Subdistrict)
	request.PostalCode = strings.TrimSpace(request.PostalCode)
	request.Note = strings.TrimSpace(request.Note)

	if err := request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := ctrl.service.Update(c.Request().Context(), request)
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
