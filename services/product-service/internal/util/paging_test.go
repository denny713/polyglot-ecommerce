package util

import (
	"net/http/httptest"
	"testing"

	"product-service/internal/testutil"

	"github.com/shopspring/decimal"
	"github.com/stretchr/testify/require"
)

func TestIntQueryParam(t *testing.T) {
	tests := []struct {
		name    string
		query   string
		want    int
		wantErr string
	}{
		{name: "missing", query: "", want: 0},
		{name: "blank", query: "?page=%20", want: 0},
		{name: "parsed", query: "?page=3", want: 3},
		{name: "trimmed", query: "?page=%204%20", want: 4},
		{name: "negative", query: "?page=-2", want: -2},
		{name: "not a number", query: "?page=abc", wantErr: "page must be a valid number"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			c, _ := testutil.NewContext(httptest.NewRequest("GET", "/"+test.query, nil), nil)

			got, err := IntQueryParam(c, "page")
			if test.wantErr != "" {
				require.EqualError(t, err, test.wantErr)
				require.Zero(t, got)

				return
			}

			require.NoError(t, err)
			require.Equal(t, test.want, got)
		})
	}
}

func TestDecimalQueryParam(t *testing.T) {
	tests := []struct {
		name    string
		query   string
		want    decimal.Decimal
		wantErr string
	}{
		{name: "missing", query: "", want: decimal.Zero},
		{name: "blank", query: "?min_price=%20", want: decimal.Zero},
		{name: "parsed", query: "?min_price=19.99", want: decimal.RequireFromString("19.99")},
		{name: "trimmed", query: "?min_price=%2025%20", want: decimal.NewFromInt(25)},
		{name: "not a number", query: "?min_price=abc", wantErr: "min_price must be a valid number"},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			c, _ := testutil.NewContext(httptest.NewRequest("GET", "/"+test.query, nil), nil)

			got, err := DecimalQueryParam(c, "min_price")
			if test.wantErr != "" {
				require.EqualError(t, err, test.wantErr)
				require.True(t, got.IsZero())

				return
			}

			require.NoError(t, err)
			require.True(t, test.want.Equal(got), "want %s, got %s", test.want, got)
		})
	}
}
