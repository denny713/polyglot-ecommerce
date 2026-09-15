package supplier

import (
	"context"
	"strings"
	"testing"
	"time"

	"product-service/internal/account"
	"product-service/internal/constant"
	"product-service/internal/dto/base"
	"product-service/internal/model"

	"github.com/google/uuid"
	"github.com/stretchr/testify/require"
)

// caller is the account the token middleware would have put on the context, the
// subject of the verified access token the request carried.
var caller = uuid.MustParse("9a7c1d2e-0000-4000-8000-00000000009a")

func callerContext() context.Context {
	return account.WithUserLogin(context.Background(), caller)
}

func sampleSupplier() model.Supplier {
	created := time.Date(2024, time.January, 2, 3, 4, 5, 0, time.UTC)

	return model.Supplier{
		Id:            7,
		Name:          "PT Maju",
		Phone:         "0211234567",
		Email:         "sales@maju.test",
		ContactPerson: "Budi",
		Address:       "Jl. Merdeka 1",
		Province:      "DKI Jakarta",
		City:          "Jakarta Pusat",
		District:      "Gambir",
		Subdistrict:   "Petojo",
		PostalCode:    "10110",
		Note:          "Pemasok utama",
		Base: model.Base{
			IsActive:  true,
			CreatedAt: created,
			UpdatedAt: created.Add(time.Hour),
		},
	}
}

func validCreateReq() SupplierCreateReq {
	return SupplierCreateReq{
		Name:          "PT Maju",
		Phone:         "0211234567",
		Email:         "sales@maju.test",
		ContactPerson: "Budi",
		Address:       "Jl. Merdeka 1",
		Province:      "DKI Jakarta",
		City:          "Jakarta Pusat",
		District:      "Gambir",
		Subdistrict:   "Petojo",
		PostalCode:    "10110",
		Note:          "Pemasok utama",
	}
}

func TestCreateReqToObjectModel(t *testing.T) {
	before := time.Now()
	got := validCreateReq().ToObjectModel(callerContext())

	require.Equal(t, "PT Maju", got.Name)
	require.Equal(t, "sales@maju.test", got.Email)
	require.Equal(t, "Budi", got.ContactPerson)
	require.Equal(t, "Jl. Merdeka 1", got.Address)
	require.Equal(t, "10110", got.PostalCode)
	require.Equal(t, "Pemasok utama", got.Note)
	require.True(t, got.IsActive)
	require.False(t, got.CreatedAt.Before(before))

	// The row is audited to the account the access token was issued for.
	require.Equal(t, caller, got.CreatedBy)
	require.Equal(t, caller, got.UpdatedBy)
}

func TestCreateReqValidate(t *testing.T) {
	tests := []struct {
		name    string
		mutate  func(*SupplierCreateReq)
		wantErr string
	}{
		{name: "a complete request is accepted", mutate: func(*SupplierCreateReq) {}},
		{
			name:   "the optional fields may be left out",
			mutate: func(r *SupplierCreateReq) { *r = SupplierCreateReq{Name: "PT Maju"} },
		},
		{name: "the name is required", mutate: func(r *SupplierCreateReq) { r.Name = "" }, wantErr: "name is required"},
		{
			name:    "the name is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.Name = strings.Repeat("a", 51) },
			wantErr: "name must not exceed 50 characters",
		},
		{
			name:    "the phone is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.Phone = strings.Repeat("1", 51) },
			wantErr: "phone must not exceed 50 characters",
		},
		{
			name:    "the email is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.Email = strings.Repeat("a", 45) + "@ab.test" },
			wantErr: "email must not exceed 50 characters",
		},
		{
			name:    "the email must be an address",
			mutate:  func(r *SupplierCreateReq) { r.Email = "not-an-email" },
			wantErr: "email must be a valid email address",
		},
		{
			name:    "the contact person is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.ContactPerson = strings.Repeat("a", 51) },
			wantErr: "contact_person must not exceed 50 characters",
		},
		{
			name:    "the province is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.Province = strings.Repeat("a", 101) },
			wantErr: "province must not exceed 100 characters",
		},
		{
			name:    "the city is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.City = strings.Repeat("a", 101) },
			wantErr: "city must not exceed 100 characters",
		},
		{
			name:    "the district is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.District = strings.Repeat("a", 101) },
			wantErr: "district must not exceed 100 characters",
		},
		{
			name:    "the subdistrict is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.Subdistrict = strings.Repeat("a", 101) },
			wantErr: "subdistrict must not exceed 100 characters",
		},
		{
			name:    "the postal code is bounded by its column",
			mutate:  func(r *SupplierCreateReq) { r.PostalCode = "12345678" },
			wantErr: "postal_code must not exceed 7 characters",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			request := validCreateReq()
			test.mutate(&request)

			err := request.Validate()
			if test.wantErr == "" {
				require.NoError(t, err)

				return
			}

			require.EqualError(t, err, test.wantErr)
		})
	}
}

func TestToResponse(t *testing.T) {
	supplier := sampleSupplier()
	got := ToResponse(supplier)

	require.Equal(t, SupplierCreateRes{
		Id:            7,
		Name:          "PT Maju",
		Phone:         "0211234567",
		Email:         "sales@maju.test",
		ContactPerson: "Budi",
		Address:       "Jl. Merdeka 1",
		Province:      "DKI Jakarta",
		City:          "Jakarta Pusat",
		District:      "Gambir",
		Subdistrict:   "Petojo",
		PostalCode:    "10110",
		Note:          "Pemasok utama",
		IsActive:      true,
		CreatedAt:     supplier.CreatedAt,
		UpdatedAt:     supplier.UpdatedAt,
	}, got)
}

func TestToSupplierDetailRes(t *testing.T) {
	supplier := sampleSupplier()
	supplier.IsDeleted = true

	got := ToSupplierDetailRes(supplier)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "PT Maju", got.Name)
	require.Equal(t, "Gambir", got.District)
	require.True(t, got.IsActive)
	require.True(t, got.IsDeleted)
	require.Equal(t, supplier.CreatedAt, got.CreatedAt)
}

func TestStatusMappers(t *testing.T) {
	supplier := sampleSupplier()

	require.Equal(t, SupplierActivateRes{Id: 7, Name: "PT Maju", Status: constant.Active},
		ToSupplierActivateRes(supplier))
	require.Equal(t, SupplierDeactivateRes{Id: 7, Name: "PT Maju", Status: constant.Inactive},
		ToSupplierDeactivateRes(supplier))
	require.Equal(t, SupplierDeleteRes{Id: 7, Name: "PT Maju", Status: constant.Delete},
		ToSupplierDeleteRes(supplier))
}

func TestUpdateReqToObjectModel(t *testing.T) {
	existing := sampleSupplier()
	actor := uuid.MustParse("2b1f8f4a-0000-4000-8000-00000000002a")
	existing.CreatedBy = actor
	existing.UpdatedBy = actor

	before := time.Now()
	got := SupplierUpdateReq{
		Id:            7,
		Name:          "PT Maju Jaya",
		Phone:         "0217654321",
		Email:         "info@maju.test",
		ContactPerson: "Sari",
		Address:       "Jl. Merdeka 2",
		Province:      "Jawa Barat",
		City:          "Bandung",
		District:      "Coblong",
		Subdistrict:   "Dago",
		PostalCode:    "40135",
		Note:          "Alamat baru",
	}.ToObjectModel(callerContext(), existing)

	require.Equal(t, "PT Maju Jaya", got.Name)
	require.Equal(t, "info@maju.test", got.Email)
	require.Equal(t, "Bandung", got.City)
	require.Equal(t, "Alamat baru", got.Note)

	// The status flags and the creation trail of the existing row survive.
	require.True(t, got.IsActive)
	require.Equal(t, actor, got.CreatedBy)
	require.Equal(t, existing.CreatedAt, got.CreatedAt)
	require.Equal(t, caller, got.UpdatedBy)
	require.False(t, got.UpdatedAt.Before(before))
}

func TestUpdateReqValidate(t *testing.T) {
	require.NoError(t, SupplierUpdateReq{Id: 1, Name: "PT Maju"}.Validate())
	require.EqualError(t, SupplierUpdateReq{Name: "PT Maju"}.Validate(), "id is required")
	require.EqualError(t, SupplierUpdateReq{Id: 1}.Validate(), "name is required")

	// The shared profile rules are enforced on the update payload too.
	require.EqualError(t, SupplierUpdateReq{Id: 1, Name: "PT Maju", Email: "nope"}.Validate(),
		"email must be a valid email address")
}

func TestToSupplierUpdateRes(t *testing.T) {
	supplier := sampleSupplier()
	got := ToSupplierUpdateRes(supplier)

	require.Equal(t, int64(7), got.Id)
	require.Equal(t, "PT Maju", got.Name)
	require.Equal(t, "Petojo", got.Subdistrict)
	require.True(t, got.IsActive)
	require.Equal(t, supplier.UpdatedAt, got.UpdatedAt)
}

func TestSearchReqValidate(t *testing.T) {
	tests := []struct {
		name    string
		given   SupplierSearchReq
		wantErr string
	}{
		{name: "an empty request applies no filter", given: SupplierSearchReq{}},
		{
			name:  "a known sortBy is accepted whatever its casing",
			given: SupplierSearchReq{Paging: base.Paging{SortBy: " CONTACT_PERSON ", SortOrder: " DESC "}},
		},
		{
			name:  "sorting by city is allowed",
			given: SupplierSearchReq{Paging: base.Paging{SortBy: "city"}},
		},
		{
			name:    "an unknown sortBy is rejected",
			given:   SupplierSearchReq{Paging: base.Paging{SortBy: "password"}},
			wantErr: "sort_by must be one of id, name, email, contact_person, province, city, created_at, or updated_at",
		},
		{
			name:    "an unknown sortOrder is rejected",
			given:   SupplierSearchReq{Paging: base.Paging{SortOrder: "sideways"}},
			wantErr: "sort_order must be one of asc or desc",
		},
		{
			name:    "a negative page is rejected",
			given:   SupplierSearchReq{Paging: base.Paging{Page: -1}},
			wantErr: "page and page_size must not be negative",
		},
		{
			name:    "a negative page size is rejected",
			given:   SupplierSearchReq{Paging: base.Paging{PageSize: -1}},
			wantErr: "page and page_size must not be negative",
		},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			err := test.given.Validate()
			if test.wantErr == "" {
				require.NoError(t, err)

				return
			}

			require.EqualError(t, err, test.wantErr)
		})
	}
}

func TestSearchReqNormalize(t *testing.T) {
	got := SupplierSearchReq{
		Name:          "  maju  ",
		Phone:         "  021  ",
		Email:         "  sales  ",
		ContactPerson: "  budi  ",
		Province:      "  dki  ",
		City:          "  jakarta  ",
		District:      "  gambir  ",
		Subdistrict:   "  petojo  ",
		PostalCode:    "  10110  ",
		Paging:        base.Paging{SortBy: "CITY", SortOrder: "ASC"},
	}.Normalize()

	require.Equal(t, "maju", got.Name)
	require.Equal(t, "021", got.Phone)
	require.Equal(t, "sales", got.Email)
	require.Equal(t, "budi", got.ContactPerson)
	require.Equal(t, "dki", got.Province)
	require.Equal(t, "jakarta", got.City)
	require.Equal(t, "gambir", got.District)
	require.Equal(t, "petojo", got.Subdistrict)
	require.Equal(t, "10110", got.PostalCode)
	require.Equal(t, "city", got.SortBy)
	require.Equal(t, constant.SortOrderAsc, got.SortOrder)
	require.Equal(t, constant.DefaultPage, got.Page)
	require.Equal(t, constant.DefaultPageSize, got.PageSize)
}

func TestToSupplierSearchRes(t *testing.T) {
	got := ToSupplierSearchRes([]model.Supplier{sampleSupplier(), {Id: 8, Name: "PT Sentosa"}})

	require.Len(t, got.Data, 2)
	require.Equal(t, int64(7), got.Data[0].Id)
	require.Equal(t, "PT Sentosa", got.Data[1].Name)

	require.NotNil(t, ToSupplierSearchRes(nil).Data)
	require.Empty(t, ToSupplierSearchRes(nil).Data)
}

func TestAllowedSortBy(t *testing.T) {
	allowed := allowedSortBy()

	for _, field := range []string{"id", "name", "email", "contact_person", "province", "city",
		"created_at", "updated_at"} {
		require.True(t, allowed[field], field)
	}

	require.False(t, allowed["password"])
}
