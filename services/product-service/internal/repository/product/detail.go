package product

import (
	"fmt"
	"product-service/internal/model"

	"gorm.io/gorm"
)

// newestFirst is the preload condition of a relation that is read as a trail,
// the rows the query brings back are the live ones with the latest first.
func newestFirst(db *gorm.DB) *gorm.DB {
	return db.Where("is_deleted = FALSE").Order("created_at DESC")
}

// Detail implement repository for get product detail
func (r repository) Detail(orm *gorm.DB, param string, value interface{}, withStockHistory bool) (model.Product, error) {
	var product model.Product

	query := orm.
		Preload("Category", "is_deleted = FALSE").
		Preload("Supplier", "is_deleted = FALSE")

	if withStockHistory {
		query = query.Preload("StockPosition", "is_deleted = FALSE").
			Preload("Stock", "is_deleted = FALSE").
			Preload("Stock.PurchaseOrder", newestFirst).
			Preload("Stock.PurchaseOrder.Supplier", "is_deleted = FALSE").
			Preload("Stock.PurchaseOrder.PurchaseOrderDetail", "is_deleted = FALSE").
			Preload("Stock.PurchaseOrder.PurchaseOrderDetail.Product", "is_deleted = FALSE").
			Preload("Stock.PurchaseReturn", newestFirst).
			Preload("Stock.PurchaseReturn.Supplier", "is_deleted = FALSE").
			Preload("Stock.PurchaseReturn.PurchaseReturnDetail", "is_deleted = FALSE").
			Preload("Stock.PurchaseReturn.PurchaseReturnDetail.Product", "is_deleted = FALSE")
	}

	err := query.Where(fmt.Sprintf("%s = ? AND is_deleted = FALSE", param), value).
		First(&product).Error
	if err != nil {
		return product, err
	}

	return product, nil
}
