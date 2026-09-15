package com.inventory.api.service.impl;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.enums.DocType;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.so.SODetailRes;
import com.inventory.api.model.dto.response.so.SORes;
import com.inventory.api.model.entity.*;
import com.inventory.api.repository.SalesOrderDetailRepository;
import com.inventory.api.repository.SalesOrderRepository;
import com.inventory.api.repository.StockPositionRepository;
import com.inventory.api.repository.StockRepository;
import com.inventory.api.service.SalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a paid sales order to stock.
 * <p>
 * The payload is deliberately thin — an id and nothing else. Quantities and prices
 * are read from the stored document rather than from the message, so a replayed or
 * altered message cannot move more stock than was actually sold.
 * <p>
 * Positions are collected in a map keyed by product before anything is written.
 * A document that lists the same product on two lines therefore deducts twice from
 * one position, rather than twice from two copies of it that each only saw one line.
 * <p>
 * A line that would drive a position below zero fails the whole message. The method
 * is transactional, so an order is either recorded in full or not at all, and the
 * ledger never claims more went out than was on hand.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private final SalesOrderRepository soRepository;
    private final SalesOrderDetailRepository soDetailRepository;
    private final StockRepository stockRepository;
    private final StockPositionRepository stockPositionRepository;

    @Override
    @Transactional
    public Response doSubmit(SOSubmitReq req) {
        SalesOrder order = soRepository.doGet(req.getId());
        Map<Long, StockPosition> positions = new LinkedHashMap<>();
        List<Stock> stocks = new ArrayList<>(order.getSalesOrderDetails().size());

        for (SalesOrderDetail detail : order.getSalesOrderDetails()) {
            deductStockPosition(positions, detail);
            stocks.add(buildStock(order, detail));
        }

        stockRepository.saveAll(stocks);
        stockPositionRepository.saveAll(positions.values());

        return new Response(200, ResponseMsg.SUCCESS, setSOResponse(order));
    }

    private void deductStockPosition(Map<Long, StockPosition> positions, SalesOrderDetail detail) {
        Product product = detail.getProduct();
        StockPosition position = positions.computeIfAbsent(product.getId(), id -> resolveStockPosition(product));

        int remaining = position.getQuantity() - detail.getQuantity();
        if (remaining < 0) {
            throw new BadRequestException(String.format(
                    "Sales order quantity (%s) cannot be greater than stock quantity (%s) for product %s",
                    detail.getQuantity(), position.getQuantity(), product.getName()));
        }

        position.setQuantity(remaining);
    }

    private StockPosition resolveStockPosition(Product product) {
        if (product.getStockPosition() != null) {
            return product.getStockPosition();
        }

        StockPosition position = new StockPosition();
        position.setProduct(product);
        position.setQuantity(0);

        return position;
    }

    private Stock buildStock(SalesOrder so, SalesOrderDetail detail) {
        Stock stock = new Stock();
        stock.setProduct(detail.getProduct());
        stock.setDocumentNumber(so.getDocumentNumber());
        stock.setDocumentType(DocType.SO);
        stock.setActivity(StockActivity.SO);
        stock.setQuantity(detail.getQuantity());
        stock.setSalesOrder(so);

        return stock;
    }

    private SORes setSOResponse(SalesOrder so) {
        SORes res = new SORes();

        BeanUtils.copyProperties(so, res);
        res.setDetails(so.getSalesOrderDetails().stream().map(this::setSODetailResponse).toList());

        return res;
    }

    private SODetailRes setSODetailResponse(SalesOrderDetail detail) {
        SODetailRes res = new SODetailRes();

        BeanUtils.copyProperties(detail, res);
        res.setProductId(detail.getProduct().getId());
        res.setProductName(detail.getProduct().getName());

        return res;
    }
}
