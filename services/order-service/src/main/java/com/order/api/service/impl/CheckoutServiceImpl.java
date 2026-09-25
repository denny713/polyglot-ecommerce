package com.order.api.service.impl;

import com.order.api.configuration.CacheConfig.CartCacheProperties;
import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.constant.ResponseMsg;
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

    private static final String DOC_TYPE = "SO";
    private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    private final DocumentNumberRepository docNoRepository;
    private final ProductRepository productRepository;
    private final SalesOrderRepository soRepository;
    private final SalesOrderDetailRepository soDetailRepository;
    private final StockPositionRepository stockPositionRepository;
    private final RedisTemplate<String, Object> cartRedisTemplate;
    private final CartCacheProperties cartCacheProperties;
    private final CheckoutProperties checkoutProperties;

    @Override
    @Transactional
    public Response doCheckout(CheckoutReq req) {
        UUID userLogin = userLogin();
        boolean fromCart = Boolean.TRUE.equals(req.getFromCart());
        List<CheckoutDetailReq> items = req.getItems();

        validateNoDuplicate(items);
        if (fromCart) {
            matchCart(userLogin, items);
        }

        Map<Long, Product> products = loadProducts(items);
        validateStock(items, products);

        SalesOrder order = new SalesOrder();
        order.setDocumentNumber(docNoRepository.generateDocumentNumber(DOC_TYPE, LocalDate.now(ZONE)));
        order.setStatus(SalesStatus.PENDING);

        List<SalesOrderDetail> details = items.stream()
                .map(item -> buildDetail(order, products.get(item.getProductId()), item.getQuantity()))
                .toList();
        order.setGrandTotal(details.stream()
                .map(SalesOrderDetail::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        soRepository.save(order);
        order.setSalesOrderDetails(soDetailRepository.saveAll(details));

        if (fromCart) {
            clearCartAfterCommit(userLogin, items);
        }

        log.info("Sales order {} created with {} product(s)", order.getDocumentNumber(), details.size());
        return new Response(200, ResponseMsg.SUCCESS, setCheckoutResponse(order));
    }

    private void validateNoDuplicate(List<CheckoutDetailReq> items) {
        Set<Long> seen = new HashSet<>();
        for (CheckoutDetailReq item : items) {
            if (!seen.add(item.getProductId())) {
                throw new BadRequestException(String.format(
                        "Product with id %s appears more than once", item.getProductId()));
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
            CheckoutDetailReq item = items.get(i);
            Object quantity = (quantities == null) ? null : quantities.get(i);

            if (!(quantity instanceof Number cartQuantity)) {
                throw new NotFoundException("Data Product with id " + item.getProductId() + " not found in cart");
            }

            if (cartQuantity.intValue() != item.getQuantity()) {
                throw new BadRequestException(String.format(
                        "Cart has changed for product with id %s, please refresh", item.getProductId()));
            }
        }
    }

    private Map<Long, Product> loadProducts(List<CheckoutDetailReq> items) {
        Map<Long, Product> products = productRepository.doList(productIds(items)).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        for (CheckoutDetailReq item : items) {
            Product product = products.get(item.getProductId());
            if (product == null) {
                throw new NotFoundException("Data Product with id " + item.getProductId() + " not found");
            }

            if (!Boolean.TRUE.equals(product.getIsActive())) {
                throw new BadRequestException(String.format(
                        "Product %s is no longer available", product.getName()));
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
            int available = Math.max(0, onHand.getOrDefault(item.getProductId(), 0)
                    - reserved.getOrDefault(item.getProductId(), 0));
            if (item.getQuantity() > available) {
                throw new BadRequestException(String.format(
                        "Quantity (%s) cannot be greater than available stock (%s) for product %s",
                        item.getQuantity(), available, products.get(item.getProductId()).getName()));
            }
        }
    }

    private static Map<Long, Integer> toMap(List<ProductQuantity> quantities) {
        return quantities.stream().collect(Collectors.toMap(
                ProductQuantity::getProductId,
                quantity -> Objects.requireNonNullElse(quantity.getQuantity(), 0)));
    }

    /** The price is always the product's current selling price, never one the caller sent. */
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

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear.run();
                }
            });
        } else {
            clear.run();
        }
    }

    private List<String> cartKeys(UUID userLogin, List<CheckoutDetailReq> items) {
        return items.stream()
                .map(item -> cartCacheProperties.keyFor(userLogin, item.getProductId()))
                .toList();
    }

    private static List<Long> productIds(List<CheckoutDetailReq> items) {
        return items.stream().map(CheckoutDetailReq::getProductId).toList();
    }

    private CheckoutRes setCheckoutResponse(SalesOrder order) {
        List<CheckoutDetailRes> items = order.getSalesOrderDetails().stream()
                .map(detail -> new CheckoutDetailRes(
                        detail.getId(),
                        detail.getProduct().getId(),
                        detail.getProduct().getName(),
                        detail.getQuantity(),
                        detail.getUnitPrice(),
                        detail.getSubtotal()))
                .toList();

        return new CheckoutRes(order.getId(), order.getDocumentNumber(), order.getGrandTotal(),
                order.getStatus(), order.getCreatedAt(),
                order.getCreatedAt().plus(checkoutProperties.paymentTimeout()), items);
    }

    /** The account behind the access token, which is who the order is for. */
    private UUID userLogin() {
        UUID userLogin = AccountUtil.getUserLogin();
        if (userLogin == null) {
            throw new ForbiddenException("You don't have permission to access this resource");
        }

        return userLogin;
    }
}
