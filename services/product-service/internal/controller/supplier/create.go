package supplier

import (
	"net/http"
	"strings"

	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/supplier"
	"product-service/internal/exception"

	"github.com/labstack/echo/v5"
)

// Create godoc
// @Summary Create a new supplier
// @Description Create a new supplier, the payload is sent as a json body.
// @Tags Supplier
// @Accept  json
// @Produce  json
// @Param request body supplier.SupplierCreateReq true "Supplier payload"
// @Success 201 {object} dto.Response{data=supplier.SupplierCreateRes}
// @Failure 400 {object} dto.Response
// @Failure 401 {object} dto.Response
// @Failure 403 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Security BearerAuth
// @Router /api/supplier [post]
func (ctrl Controller) Create(c *echo.Context) error {
	var request supplier.SupplierCreateReq
	if err := c.Bind(&request); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, "request body must be a valid json")
	}

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

	response, err := ctrl.service.Create(c.Request().Context(), request)
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusCreated, dto.Response{
		Status:  http.StatusCreated,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}
