package product

import (
	"product-service/internal/model"
	"time"

	"github.com/shopspring/decimal"
)

type (
	ProductHistoryReq struct {
		Id int64
	}

	ProductHistoryRes struct {
		Id            int64           `json:"id"`
		Name          string          `json:"name"`
		Description   string          `json:"description"`
		BuyPrice      decimal.Decimal `json:"buy_price"`
		SellPrice     decimal.Decimal `json:"sell_price"`
		StockQuantity int             `jsin:"stock_quantity"`
		ImageUrl      string          `json:"image_url"`
		Category      string          `json:"category"`
		Supplier      string          `json:"supplier"`
		IsActive      bool            `json:"is_active"`
		IsDeleted     bool            `json:"is_deleted"`
		CreatedAt     time.Time       `json:"created_at"`
		UpdatedAt     time.Time       `json:"updated_at"`
		StockHistory  []StockRes      `json:"stock_history"`
	}

	StockRes struct {
		Id             int64              `json:"id"`
		DocumentNumber string             `json:"document_number"`
		DocumentType   string             `json:"document_type"`
		Activity       string             `json:"activity"`
		Quantity       int                `json:"quantity"`
		CreatedAt      time.Time          `json:"created_at"`
		PurchaseOrder  *PurchaseOrderRes  `json:"purchaseOrder"`
		PurchaseReturn *PurchaseReturnRes `json:"purchase_return"`
	}

	PurchaseOrderRes struct {
		Id              int64                    `json:"id"`
		Supplier        string                   `json:"supplier"`
		Status          string                   `json:"status"`
		OrderGrandTotal decimal.Decimal          `json:"order_grand_total"`
		RealGrandTotal  decimal.Decimal          `json:"real_grand_total"`
		Note            string                   `json:"note"`
		Detail          []PurchaseOrderDetailRes `json:"detail"`
	}

	PurchaseOrderDetailRes struct {
		Id            int64           `json:"id"`
		Product       string          `json:"product"`
		OrderQuantity int             `json:"order_quantity"`
		RealQuantity  int             `json:"real_quantity"`
		UnitPrice     decimal.Decimal `json:"unit_price"`
		OrderSubtotal decimal.Decimal `json:"order_subtotal"`
		RealSubtotal  decimal.Decimal `json:"real_subtotal"`
		Note          string          `json:"note"`
	}

	PurchaseReturnRes struct {
		Id         int64                     `json:"id"`
		Supplier   string                    `json:"supplier"`
		Status     string                    `json:"status"`
		GrandTotal decimal.Decimal           `json:"grand_total"`
		Reason     string                    `json:"reason"`
		Note       string                    `json:"note"`
		Detail     []PurchaseReturnDetailRes `json:"detail"`
	}

	PurchaseReturnDetailRes struct {
		Id        int64           `json:"id"`
		Product   string          `json:"product"`
		Quantity  int             `json:"quantity"`
		UnitPrice decimal.Decimal `json:"unit_price"`
		Subtotal  decimal.Decimal `json:"subtotal"`
		Reason    string          `json:"reason"`
		Note      string          `json:"note"`
	}
)

// ToProductHistoryRes mapping the table model.Product to the response object.
func ToProductHistoryRes(product model.Product) ProductHistoryRes {
	var stocks []StockRes

	result := ProductHistoryRes{
		Id:          product.Id,
		Name:        product.Name,
		Description: product.Description,
		BuyPrice:    product.BuyPrice,
		SellPrice:   product.SellPrice,
		ImageUrl:    product.ImageURL,
		IsActive:    product.IsActive,
		IsDeleted:   product.IsDeleted,
		CreatedAt:   product.CreatedAt,
		UpdatedAt:   product.UpdatedAt,
	}

	if product.Category != nil {
		result.Category = product.Category.Name
	}

	if product.Supplier != nil {
		result.Supplier = product.Supplier.Name
	}

	if product.StockPosition != nil {
		result.StockQuantity = product.StockPosition.Quantity
	}

	// Every relation below is preloaded under an is_deleted filter, so a soft
	// deleted document, supplier or product comes back as a nil pointer rather
	// than as a row. Each one is therefore read through a guard, and what was
	// filtered out is reported as an empty value.
	if product.Stock != nil {
		stocks = mapStockHistory(*product.Stock)
	}

	result.StockHistory = stocks

	return result
}

// mapStockHistory maps the movements of a product onto the response, newest
// first as the repository read them.
func mapStockHistory(movements []model.Stock) []StockRes {
	var stocks []StockRes

	for _, stock := range movements {
		stockRes := StockRes{}

		stockRes.Id = stock.Id
		stockRes.DocumentNumber = stock.DocumentNumber
		stockRes.DocumentType = stock.DocumentType
		stockRes.Activity = stock.Activity
		stockRes.Quantity = stock.Quantity
		stockRes.CreatedAt = stock.CreatedAt

		if stock.PurchaseOrder != nil {
			po := PurchaseOrderRes{
				Id:              stock.PurchaseOrderId,
				Status:          stock.PurchaseOrder.Status,
				OrderGrandTotal: stock.PurchaseOrder.OrderGrandTotal,
				RealGrandTotal:  stock.PurchaseOrder.RealGrandTotal,
				Note:            stock.PurchaseOrder.Note,
				Detail:          []PurchaseOrderDetailRes{},
			}

			if stock.PurchaseOrder.Supplier != nil {
				po.Supplier = stock.PurchaseOrder.Supplier.Name
			}

			if stock.PurchaseOrder.PurchaseOrderDetail != nil {
				for _, detail := range *stock.PurchaseOrder.PurchaseOrderDetail {
					poDetail := PurchaseOrderDetailRes{}

					poDetail.Id = detail.Id
					poDetail.OrderQuantity = detail.OrderQuantity
					poDetail.RealQuantity = detail.RealQuantity
					poDetail.UnitPrice = detail.UnitPrice
					poDetail.OrderSubtotal = detail.OrderSubtotal
					poDetail.RealSubtotal = detail.RealSubtotal
					poDetail.Note = detail.Note

					if detail.Product != nil {
						poDetail.Product = detail.Product.Name
					}

					po.Detail = append(po.Detail, poDetail)
				}
			}

			stockRes.PurchaseOrder = &po
		}

		if stock.PurchaseReturn != nil {
			pr := PurchaseReturnRes{
				Id:         stock.PurchaseReturnId,
				Status:     stock.PurchaseReturn.Status,
				GrandTotal: stock.PurchaseReturn.GrandTotal,
				Reason:     stock.PurchaseReturn.Reason,
				Note:       stock.PurchaseReturn.Note,
				Detail:     []PurchaseReturnDetailRes{},
			}

			if stock.PurchaseReturn.Supplier != nil {
				pr.Supplier = stock.PurchaseReturn.Supplier.Name
			}

			if stock.PurchaseReturn.PurchaseReturnDetail != nil {
				for _, detail := range *stock.PurchaseReturn.PurchaseReturnDetail {
					prDetail := PurchaseReturnDetailRes{}

					prDetail.Id = detail.Id
					prDetail.Quantity = detail.Quantity
					prDetail.UnitPrice = detail.UnitPrice
					prDetail.Subtotal = detail.Subtotal
					prDetail.Reason = detail.Reason
					prDetail.Note = detail.Note

					if detail.Product != nil {
						prDetail.Product = detail.Product.Name
					}

					pr.Detail = append(pr.Detail, prDetail)
				}
			}

			stockRes.PurchaseReturn = &pr
		}

		stocks = append(stocks, stockRes)
	}

	return stocks
}
