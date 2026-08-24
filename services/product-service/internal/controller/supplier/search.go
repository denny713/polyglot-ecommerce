package supplier

import (
	"net/http"
	"product-service/internal/constant"
	"product-service/internal/dto"
	"product-service/internal/dto/supplier"
	"product-service/internal/exception"
	service "product-service/internal/service/supplier"
	"product-service/internal/util"
	"strings"

	"github.com/labstack/echo/v5"
)

// Search godoc
// @Summary Search suppliers
// @Description Search suppliers by their profile and address fields (ILIKE).
// @Tags Supplier
// @Accept  json
// @Produce  json
// @Param name query string false "Supplier name, matched partially"
// @Param phone query string false "Supplier phone, matched partially"
// @Param email query string false "Supplier email, matched partially"
// @Param contact_person query string false "Supplier contact person, matched partially"
// @Param province query string false "Supplier province, matched partially"
// @Param city query string false "Supplier city, matched partially"
// @Param district query string false "Supplier district, matched partially"
// @Param subdistrict query string false "Supplier subdistrict, matched partially"
// @Param postal_code query string false "Supplier postal code, matched partially"
// @Param sort_by query string false "Sort field" Enums(id, name, email, contact_person, province, city, created_at, updated_at)
// @Param sort_order query string false "Sort direction" Enums(asc, desc)
// @Param page query integer false "Page number, starts at 1"
// @Param page_size query integer false "Rows per page, max 100"
// @Success 200 {object} dto.Response{data=supplier.SupplierSearchRes}
// @Failure 400 {object} dto.Response
// @Failure 500 {object} dto.Response
// @Router /api/supplier [get]
func Search(c *echo.Context) error {
	request, err := bindSupplierSearchReq(c)
	if err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	if err = request.Validate(); err != nil {
		return echo.NewHTTPError(http.StatusBadRequest, err.Error())
	}

	response, err := service.Search(c.Request().Context(), request)
	if err != nil {
		return exception.HTTPError(err)
	}

	return c.JSON(http.StatusOK, dto.Response{
		Status:  http.StatusOK,
		Message: constant.MsgSuccess,
		Data:    response,
	})
}

// bindSupplierSearchReq reads the filters from the query string, every parameter
// is optional so a missing one is left at its zero value.
func bindSupplierSearchReq(c *echo.Context) (supplier.SupplierSearchReq, error) {
	var request supplier.SupplierSearchReq

	request.Name = strings.TrimSpace(c.QueryParam("name"))
	request.Phone = strings.TrimSpace(c.QueryParam("phone"))
	request.Email = strings.TrimSpace(c.QueryParam("email"))
	request.ContactPerson = strings.TrimSpace(c.QueryParam("contact_person"))
	request.Province = strings.TrimSpace(c.QueryParam("province"))
	request.City = strings.TrimSpace(c.QueryParam("city"))
	request.District = strings.TrimSpace(c.QueryParam("district"))
	request.Subdistrict = strings.TrimSpace(c.QueryParam("subdistrict"))
	request.PostalCode = strings.TrimSpace(c.QueryParam("postal_code"))
	request.SortBy = strings.TrimSpace(c.QueryParam("sort_by"))
	request.SortOrder = strings.TrimSpace(c.QueryParam("sort_order"))

	page, err := util.IntQueryParam(c, "page")
	if err != nil {
		return request, err
	}

	pageSize, err := util.IntQueryParam(c, "page_size")
	if err != nil {
		return request, err
	}

	request.Page = page
	request.PageSize = pageSize

	return request, nil
}
