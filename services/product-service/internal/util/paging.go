package util

import (
	"errors"
	"strconv"
	"strings"

	"github.com/labstack/echo/v5"
	"github.com/shopspring/decimal"
)

// IntQueryParam parses an integer query parameter, an empty one is zero.
func IntQueryParam(c *echo.Context, name string) (int, error) {
	value := strings.TrimSpace(c.QueryParam(name))
	if value == "" {
		return 0, nil
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return 0, errors.New(name + " must be a valid number")
	}

	return parsed, nil
}

// DecimalQueryParam parses a decimal query parameter, an empty one is zero.
func DecimalQueryParam(c *echo.Context, name string) (decimal.Decimal, error) {
	value := strings.TrimSpace(c.QueryParam(name))
	if value == "" {
		return decimal.Zero, nil
	}

	parsed, err := decimal.NewFromString(value)
	if err != nil {
		return decimal.Zero, errors.New(name + " must be a valid number")
	}

	return parsed, nil
}
