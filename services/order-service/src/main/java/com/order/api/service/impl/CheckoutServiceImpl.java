package com.order.api.service.impl;

import com.order.api.configuration.CacheConfig.CartCacheProperties;
import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.constant.ResponseMsg;
import com.order.api.enums.DocType;
import com.order.api.enums.RefundReason;
import com.order.api.enums.SalesStatus;
import com.order.api.exception.BadRequestException;
import com.order.api.exception.ForbiddenException;
import com.order.api.exception.NotFoundException;
import com.order.api.exception.ServiceException;
import com.order.api.model.dto.request.checkout.CheckoutDetailReq;
import com.order.api.model.dto.request.checkout.CheckoutReq;
import com.order.api.model.dto.response.Response;
import com.order.api.model.dto.response.checkout.CheckoutDetailRes;
import com.order.api.model.dto.response.checkout.CheckoutRes;
import com.order.api.model.dto.response.checkout.ProductQuantity;
import com.order.api.model.entity.Product;
import com.order.api.model.entity.SalesOrder;
import com.order.api.model.entity.SalesOrderDetail;
import com.order.api.repository.DocumentNumberRepository;
import com.order.api.repository.ProductRepository;
import com.order.api.repository.SalesOrderDetailRepository;
import com.order.api.repository.SalesOrderRepository;
import com.order.api.repository.StockPositionRepository;
import com.order.api.service.CheckoutService;
import com.order.api.service.RefundService;
import com.order.api.util.AccountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates a pending sales order from the products a customer picked, either out of
 * their cart or bought straight away. The inventory service deducts stock only once
 * the order is paid, so until then a pending order holds its stock by being counted
 * against it, for as long as its payment window lasts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    private final DocumentNumberRepository docNoRepository;
    private final ProductRepository productRepository;
    private final SalesOrderRepository soRepository;
    private final SalesOrderDetailRepository soDetailRepository;
    private final StockPositionRepository stockPositionRepository;
    private final RefundService refundService;
    private final RedisTemplate<String, Object> cartRedisTemplate;
    private final CartCacheProperties cartCacheProperties;
    private final CheckoutProperties checkoutProperties;

    /**
     * Checks the lines against the cart (when they come from it), the catalogue and the
     * stock, then saves them as one pending sales order. Checked-out lines leave the
     * cart only after the order commits.
     */
    @Override
    @Transactional
    public Response doCheckout(CheckoutReq req) {
        UUID userLogin = AccountUtil.requireUserLogin();
        boolean fromCart = Boolean.TRUE.equals(req.getFromCart());
        List<CheckoutDetailReq> items = req.getItems();

        validateNoDuplicate(items);
        if (fromCart) {
            matchCart(userLogin, items);
        }

        Map<Long, Product> products = loadProducts(items);
        validateStock(items, products);

        SalesOrder order = new SalesOrder();
        order.setDocumentNumber(docNoRepository.generateDocumentNumber(DocType.SALES_ORDER, LocalDate.now(ZONE)));
        order.setStatus(SalesStatus.PENDING);

        List<SalesOrderDetail> details = items.stream()
                .map(item -> buildDetail(order, products.get(item.getProductId()), item.getQuantity()))
                .toList();
        order.setGrandTotal(details.stream()
                .map(SalesOrderDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        order.setPaid(BigDecimal.ZERO);
        order.setOutstanding(order.getGrandTotal());

        soRepository.save(order);
        order.setSalesOrderDetails(soDetailRepository.saveAll(details));

        if (fromCart) {
            clearCartAfterCommit(userLogin, items);
        }

        log.info("Sales order {} created with {} product(s)", order.getDocumentNumber(), details.size());
        return success(order);
    }

    /**
     * Cancels a pending sales order, which releases the stock it was holding, and
     * refunds whatever was paid towards it in part. A paid order has had its stock
     * taken by the inventory service, so it is cancelled through payment instead,
     * which also puts that stock back. The order's row stays locked until this
     * commits, so a payment arriving meanwhile waits and then finds it cancelled.
     */
    @Override
    @Transactional
    public Response doCancel(Long checkoutId) {
        UUID userLogin = AccountUtil.requireUserLogin();
        SalesOrder order = lockOrder(checkoutId);
        if (order.getCreatedBy() == null || !order.getCreatedBy().equals(userLogin)) {
            throw new ForbiddenException("You don't have permission to cancel this sales order");
        }

        if (order.getStatus() != SalesStatus.PENDING) {
            throw new BadRequestException("Only a pending order can be cancelled");
        }

        order.setStatus(SalesStatus.CANCELLED);
        soRepository.save(order);
        refundPaid(order, RefundReason.CANCELLATION);

        log.info("Sales order {} successfully cancelled", order.getDocumentNumber());
        return success(order);
    }

    /**
     * Expires one pending order whose payment window has run out and refunds what was
     * paid towards it, for the expiry job. Says whether it expired the order: one paid
     * in full or cancelled since the job listed it is left alone.
     */
    @Override
    @Transactional
    public boolean doExpire(Long checkoutId) {
        SalesOrder order = lockOrder(checkoutId);
        LocalDateTime cutoff = LocalDateTime.now().minus(checkoutProperties.paymentTimeout());
        if (order.getStatus() != SalesStatus.PENDING || order.getCreatedAt().isAfter(cutoff)) {
            return false;
        }

        order.setStatus(SalesStatus.EXPIRED);
        soRepository.save(order);
        refundPaid(order, RefundReason.EXPIRED);

        log.info("Sales order {} expired", order.getDocumentNumber());
        return true;
    }

    private SalesOrder lockOrder(Long checkoutId) {
        return soRepository.lockById(checkoutId)
                .orElseThrow(() -> new NotFoundException("Data Sales Order with id " + checkoutId + " not found"));
    }

    /**
     * Nothing paid means nothing to refund.
     */
    private void refundPaid(SalesOrder order, RefundReason reason) {
        if (order.getPaid() != null && order.getPaid().signum() > 0) {
            refundService.doRefundOrder(order, reason);
        }
    }

    /**
     * Each product may appear once only, so its quantity is never split across lines.
     */
    private void validateNoDuplicate(List<CheckoutDetailReq> items) {
        Set<Long> seen = new HashSet<>();
        for (CheckoutDetailReq item : items) {
            if (!seen.add(item.getProductId())) {
                throw new BadRequestException("Product with id " + item.getProductId() + " appears more than once");
            }
        }
    }

    /**
     * Every line must be in the cart with the quantity the customer was shown. A cart
     * changed since, from another device say, is refused rather than billed as it now
     * stands.
     */
    private void matchCart(UUID userLogin, List<CheckoutDetailReq> items) {
        List<Object> quantities;
        try {
            quantities = cartRedisTemplate.opsForValue().multiGet(cartKeys(userLogin, items));
        } catch (DataAccessException e) {
            log.error("Unable to read the cart of {}", userLogin, e);
            throw new ServiceException("Failed to read the cart");
        }

        for (int i = 0; i < items.size(); i++) {
            Long productId = items.get(i).getProductId();
            Object quantity = (quantities == null) ? null : quantities.get(i);

            if (!(quantity instanceof Number cartQuantity)) {
                throw new NotFoundException("Data Product with id " + productId + " not found in cart");
            }
            if (cartQuantity.intValue() != items.get(i).getQuantity()) {
                throw new BadRequestException("Cart has changed for product with id " + productId + ", please refresh");
            }
        }
    }

    /**
     * Loads every product on the order, keyed by id. Each one must exist and still be on
     * sale.
     */
    private Map<Long, Product> loadProducts(List<CheckoutDetailReq> items) {
        Map<Long, Product> products = productRepository.doList(productIds(items)).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (CheckoutDetailReq item : items) {
            Product product = products.get(item.getProductId());
            if (product == null) {
                throw new NotFoundException("Data Product with id " + item.getProductId() + " not found");
            }
            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new BadRequestException("Product " + product.getName() + " is no longer available");
            }
        }

        return products;
    }

    /**
     * What a checkout may take is what is on hand less what other orders were promised
     * and the inventory service has not deducted yet. The stock rows stay locked until
     * this order commits, so a second checkout of the same product waits and then
     * counts this order too. A product with no stock position has never been stocked,
     * so it counts as zero.
     */
    private void validateStock(List<CheckoutDetailReq> items, Map<Long, Product> products) {
        List<Long> productIds = productIds(items);
        Map<Long, Integer> onHand = toMap(stockPositionRepository.lockQuantities(productIds));
        Map<Long, Integer> reserved = toMap(soDetailRepository.sumReserved(productIds,
                SalesStatus.PENDING.getLabel(), SalesStatus.PAID.getLabel(),
                LocalDateTime.now().minus(checkoutProperties.paymentTimeout())));

        for (CheckoutDetailReq item : items) {
            Long productId = item.getProductId();
            int available = Math.max(0, onHand.getOrDefault(productId, 0) - reserved.getOrDefault(productId, 0));
            if (item.getQuantity() > available) {
                throw new BadRequestException(String.format(
                        "Quantity (%s) cannot be greater than available stock (%s) for product %s",
                        item.getQuantity(), available, products.get(productId).getName()));
            }
        }
    }

    /**
     * The price is always the product's current selling price, never one the caller sent.
     */
    private SalesOrderDetail buildDetail(SalesOrder order, Product product, Integer quantity) {
        SalesOrderDetail detail = new SalesOrderDetail();
        detail.setSalesOrder(order);
        detail.setProduct(product);
        detail.setQuantity(quantity);
        detail.setUnitPrice(product.getSellPrice());
        detail.setSubtotal(product.getSellPrice().multiply(BigDecimal.valueOf(quantity)));
        return detail;
    }

    /**
     * Only the lines that were checked out leave the cart, and only once the order is
     * committed, so a failed checkout leaves the cart as it was.
     */
    private void clearCartAfterCommit(UUID userLogin, List<CheckoutDetailReq> items) {
        List<String> keys = cartKeys(userLogin, items);
        Runnable clear = () -> {
            try {
                cartRedisTemplate.delete(keys);
                log.info("Cart lines {} removed after checkout", keys);
            } catch (DataAccessException e) {
                // The order is already committed and must not be undone for this; a
                // line left behind could be checked out a second time, so say so loudly.
                log.error("Unable to remove cart lines {} after checkout", keys, e);
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            clear.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                clear.run();
            }
        });
    }

    /**
     * The Redis keys of the cart lines behind these items, in the same order.
     */
    private List<String> cartKeys(UUID userLogin, List<CheckoutDetailReq> items) {
        return items.stream()
                .map(item -> cartCacheProperties.keyFor(userLogin, item.getProductId()))
                .toList();
    }

    /**
     * The product ids of these items, in the same order.
     */
    private static List<Long> productIds(List<CheckoutDetailReq> items) {
        return items.stream().map(CheckoutDetailReq::getProductId).toList();
    }

    /**
     * Quantities keyed by product id, with a missing quantity counted as zero.
     */
    private static Map<Long, Integer> toMap(List<ProductQuantity> quantities) {
        return quantities.stream().collect(Collectors.toMap(
                ProductQuantity::getProductId,
                quantity -> Objects.requireNonNullElse(quantity.getQuantity(), 0)));
    }

    /**
     * Wraps the order in a successful response. Its payment deadline is the moment it
     * was created plus the payment timeout.
     */
    private Response success(SalesOrder order) {
        List<CheckoutDetailRes> items = order.getSalesOrderDetails().stream()
                .map(detail -> new CheckoutDetailRes(detail.getId(), detail.getProduct().getId(),
                        detail.getProduct().getName(), detail.getQuantity(), detail.getUnitPrice(),
                        detail.getSubtotal()))
                .toList();

        CheckoutRes res = new CheckoutRes(order.getId(), order.getDocumentNumber(), order.getGrandTotal(),
                order.getStatus(), order.getCreatedAt(),
                order.getCreatedAt().plus(checkoutProperties.paymentTimeout()), items);
        return new Response(200, ResponseMsg.SUCCESS, res);
    }
}
