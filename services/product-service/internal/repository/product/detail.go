package product

import (
	"fmt"
	"product-service/internal/configuration"
	"product-service/internal/model"
)

func Detail(param string, value interface{}) (model.Product, error) {
	var product model.Product
	err := configuration.DB.Preload("Stock").Where(
		fmt.Sprintf("%s = ? AND is_deleted = FALSE", param), value).
		First(&product).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
