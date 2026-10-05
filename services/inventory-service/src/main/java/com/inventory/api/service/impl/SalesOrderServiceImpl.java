package com.inventory.api.service.impl;

import com.inventory.api.constant.ResponseMsg;
import com.inventory.api.enums.DocType;
import com.inventory.api.enums.SalesStatus;
import com.inventory.api.enums.StockActivity;
import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.model.dto.request.so.SOCancelReq;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.model.dto.response.so.SODetailRes;
import com.inventory.api.model.dto.response.so.SORes;
import com.inventory.api.model.entity.*;
import com.inventory.api.repository.SalesOrderDetailRepository;
import com.inventory.api.repository.RefundRepository;
import com.inventory.api.repository.SalesOrderRepository;
import com.inventory.api.repository.StockPositionRepository;
import com.inventory.api.repository.StockRepository;
import com.inventory.api.service.SalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Applies a sales order to stock: a paid order takes its goods out, and a cancelled
 * one puts back what it took. Submit and cancel arrive on different queues, so either
 * may come first, and a broker may deliver either twice; the order's row lock and the
 * movements already written decide what is still to be done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private final SalesOrderRepository soRepository;
    private final SalesOrderDetailRepository soDetailRepository;
    private final StockRepository stockRepository;
    private final StockPositionRepository stockPositionRepository;
    private final RefundRepository refundRepository;

    /**
     * Takes the goods of a paid order out of stock, once. An order cancelled before
     * this ran never has its stock taken, so its cancel has nothing to put back.
     */
    @Override
    @Transactional
    public Response doSubmit(SOSubmitReq req) {
        SalesOrder order = lockOrder(req.getId());

        if (stockRepository.existsBySalesOrderIdAndActivity(order.getId(), StockActivity.SO)) {
            log.info("Sales order {} has already taken its stock, submit ignored", order.getDocumentNumber());
            return new Response(200, ResponseMsg.SUCCESS, setSOResponse(order));
        }
        if (order.getStatus() == SalesStatus.CANCELLED) {
            log.info("Sales order {} was cancelled before its stock was taken, submit ignored",
                    order.getDocumentNumber());
            return new Response(200, ResponseMsg.SUCCESS, setSOResponse(order));
        }
        if (order.getStatus() != SalesStatus.PAID) {
            throw new BadRequestException("Sales order " + order.getDocumentNumber() + " is not paid");
        }

        lockStockPositions(order);
        Map<Long, StockPosition> positions = new LinkedHashMap<>();
        List<Stock> stocks = new ArrayList<>(order.getSalesOrderDetails().size());

        for (SalesOrderDetail detail : order.getSalesOrderDetails()) {
            deductStockPosition(positions, detail);
            stocks.add(buildStock(order, detail.getProduct(), detail.getQuantity(), StockActivity.SO,
                    order.getDocumentNumber(), DocType.SO));
        }

        stockRepository.saveAll(stocks);
        stockPositionRepository.saveAll(positions.values());

        return new Response(200, ResponseMsg.SUCCESS, setSOResponse(order));
    }

    /**
     * Puts back, as stock in, exactly what the order's stock out movements took, once.
     * An order whose stock was never taken has nothing to put back. The stock in is
     * recorded under the refunds that took the order back, not the order itself: one
     * movement per refund and product, the quantity split in proportion to what each
     * refund returned.
     */
    @Override
    @Transactional
    public Response doCancel(SOCancelReq req) {
        SalesOrder order = lockOrder(req.getId());
        if (order.getStatus() != SalesStatus.CANCELLED) {
            throw new BadRequestException("Sales order " + order.getDocumentNumber() + " is not cancelled");
        }
        if (req.getRefundIds() == null || req.getRefundIds().isEmpty()) {
            throw new BadRequestException("Sales order " + order.getDocumentNumber() + " cancel has no refunds");
        }

        if (stockRepository.existsBySalesOrderIdAndActivity(order.getId(), StockActivity.SI)) {
            log.info("Sales order {} has already returned its stock, cancel ignored", order.getDocumentNumber());
            return new Response(200, ResponseMsg.SUCCESS, setSOResponse(order));
        }

        lockStockPositions(order);
        List<Stock> taken = stockRepository.findBySalesOrderIdAndActivityOrderByIdAsc(order.getId(), StockActivity.SO);
        if (taken.isEmpty()) {
            log.info("Sales order {} never took its stock, nothing to return", order.getDocumentNumber());
            return new Response(200, ResponseMsg.SUCCESS, setSOResponse(order));
        }

        List<Refund> refunds = loadRefunds(order, req.getRefundIds());
        List<int[]> shares = taken.stream().map(out -> splitByRefund(out.getQuantity(), refunds)).toList();

        Map<Long, StockPosition> positions = new LinkedHashMap<>();
        List<Stock> returned = new ArrayList<>();
        for (int r = 0; r < refunds.size(); r++) {
            for (int t = 0; t < taken.size(); t++) {
                int quantity = shares.get(t)[r];
                if (quantity == 0) {
                    continue;
                }
                Stock out = taken.get(t);
                addStockPosition(positions, out.getProduct(), quantity);
                Stock in = buildStock(order, out.getProduct(), quantity, StockActivity.SI,
                        refunds.get(r).getDocumentNumber(), DocType.SR);
                in.setSalesRefund(refunds.get(r));
                returned.add(in);
            }
        }

        stockRepository.saveAll(returned);
        stockPositionRepository.saveAll(positions.values());

        log.info("Sales order {} returned {} stock line(s) to stock as {} movement(s) across {} refund(s)",
                order.getDocumentNumber(), taken.size(), returned.size(), refunds.size());
        return new Response(200, ResponseMsg.SUCCESS, setSOResponse(order));
    }

    /**
     * Reads the refunds the message names from the order service's records, so their
     * numbers and amounts are the ones actually refunded. Every one must be a refund
     * of this order with something to split by.
     */
    private List<Refund> loadRefunds(SalesOrder order, List<Long> refundIds) {
        Set<Long> ids = new LinkedHashSet<>(refundIds);
        List<Refund> refunds = refundRepository.findBySalesOrderIdAndIdInOrderByIdAsc(order.getId(), ids);
        if (refunds.size() != ids.size()) {
            throw new BadRequestException("Sales order " + order.getDocumentNumber()
                    + " cancel names refunds that are not of this order");
        }
        for (Refund refund : refunds) {
            if (refund.getAmount() == null || refund.getAmount().signum() <= 0) {
                throw new BadRequestException("Refund " + refund.getDocumentNumber() + " has no positive amount");
            }
        }

        return refunds;
    }

    /**
     * Splits a quantity across the refunds in proportion to their amounts, by largest
     * remainder: each refund gets the whole part of its share, and the units left over
     * go to the largest fractions, the earlier refund first on a tie. The shares always
     * add up to the quantity; a refund whose share rounds down to nothing gets none.
     */
    private static int[] splitByRefund(int quantity, List<Refund> refunds) {
        BigDecimal total = refunds.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int[] shares = new int[refunds.size()];
        BigDecimal[] remainders = new BigDecimal[refunds.size()];

        int allotted = 0;
        for (int i = 0; i < refunds.size(); i++) {
            BigDecimal[] division = refunds.get(i).getAmount().multiply(BigDecimal.valueOf(quantity))
                    .divideAndRemainder(total);
            shares[i] = division[0].intValueExact();
            remainders[i] = division[1];
            allotted += shares[i];
        }

        List<Integer> byRemainder = new ArrayList<>(IntStream.range(0, refunds.size()).boxed().toList());
        byRemainder.sort(Comparator.comparing((Integer i) -> remainders[i]).reversed()
                .thenComparing(Comparator.naturalOrder()));
        for (int k = 0; k < quantity - allotted; k++) {
            shares[byRemainder.get(k)]++;
        }

        return shares;
    }

    private SalesOrder lockOrder(Long id) {
        return soRepository.lockById(id)
                .orElseThrow(() -> new NotFoundException("Data Sales Order with id " + id + " not found"));
    }

    /**
     * Locks the positions of every product on the order before any of them is read,
     * so the quantities read next are the latest and stay so until this commits.
     */
    private void lockStockPositions(SalesOrder order) {
        List<Long> productIds = soDetailRepository.findProductIds(order.getId());
        if (!productIds.isEmpty()) {
            stockPositionRepository.lockByProductIds(productIds);
        }
    }

    private void addStockPosition(Map<Long, StockPosition> positions, Product product, int quantity) {
        StockPosition position = positions.computeIfAbsent(product.getId(), id -> resolveStockPosition(product));
        position.setQuantity(position.getQuantity() + quantity);
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

    private Stock buildStock(SalesOrder so, Product product, Integer quantity, StockActivity activity,
                             String documentNumber, DocType documentType) {
        Stock stock = new Stock();
        stock.setProduct(product);
        stock.setDocumentNumber(documentNumber);
        stock.setDocumentType(documentType);
        stock.setActivity(activity);
        stock.setQuantity(quantity);
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
