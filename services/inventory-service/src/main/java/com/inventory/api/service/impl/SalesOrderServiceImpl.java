package com.inventory.api.service.impl;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.enums.DocStatus;
import com.inventory.api.enums.DocType;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.model.dto.request.so.SODetailSubmitReq;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.entity.Product;
import com.inventory.api.model.entity.PurchaseReturn;
import com.inventory.api.model.entity.PurchaseReturnDetail;
import com.inventory.api.model.entity.Stock;
import com.inventory.api.repository.StockPositionRepository;
import com.inventory.api.repository.StockRepository;
import com.inventory.api.service.SalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private final StockRepository stockRepository;
    private final StockPositionRepository stockPositionRepository;

    @Override
    @Transactional
    public Response doSubmit(SOSubmitReq req) {
        /*List<Stock> stocks = new ArrayList<>(req.getDetails().size());
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (SODetailSubmitReq detail : req.getDetails()) {
            deductStockPosition(positions, detail);
            stocks.add(buildStock(product, detail));

            grandTotal = grandTotal.add(detail.getSubtotal());
        }

        stockRepository.saveAll(stocks);
        stockPositionRepository.saveAll(positions.values());*/

        return new Response(200, ResponseMsg.SUCCESS, null);
    }

    /*private Stock buildStock(Product pr, SODetailSubmitReq detail) {
        Stock stock = new Stock();
        stock.setProduct(detail.getProduct());
        stock.setDocumentNumber(pr.getDocumentNumber());
        stock.setDocumentType(DocType.SO);
        stock.setActivity(StockActivity.SO);
        stock.setQuantity(detail.getQuantity());
        stock.setSalesOrder(so);

        return stock;
    }*/
}
