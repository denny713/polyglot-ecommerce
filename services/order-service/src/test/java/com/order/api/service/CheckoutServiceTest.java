package com.order.api.service;

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
import com.order.api.producer.NotificationProducer;
import com.order.api.repository.DocumentNumberRepository;
import com.order.api.repository.ProductRepository;
import com.order.api.repository.SalesOrderDetailRepository;
import com.order.api.repository.SalesOrderRepository;
import com.order.api.repository.StockPositionRepository;
import com.order.api.service.impl.CheckoutServiceImpl;
import com.order.api.util.AccountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for turning a customer's pick into a pending sales order. */
class CheckoutServiceTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String DOC_NO = "SO20260923001";
    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(60);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 23, 10, 15, 30);

    private DocumentNumberRepository docNoRepository;
    private ProductRepository productRepository;
    private SalesOrderRepository soRepository;
    private SalesOrderDetailRepository soDetailRepository;
    private StockPositionRepository stockPositionRepository;
    private RefundService refundService;
    private RedisTemplate<String, Object> cartRedisTemplate;
    private ValueOperations<String, Object> valueOps;
    private NotificationProducer notificationProducer;

    private CheckoutServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        docNoRepository = mock(DocumentNumberRepository.class);
        productRepository = mock(ProductRepository.class);
        soRepository = mock(SalesOrderRepository.class);
        soDetailRepository = mock(SalesOrderDetailRepository.class);
        stockPositionRepository = mock(StockPositionRepository.class);
        refundService = mock(RefundService.class);
        cartRedisTemplate = mock(RedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        notificationProducer = mock(NotificationProducer.class);
        when(cartRedisTemplate.opsForValue()).thenReturn(valueOps);

        when(docNoRepository.generateDocumentNumber(eq(DocType.SALES_ORDER), any(LocalDate.class))).thenReturn(DOC_NO);
        when(soRepository.save(any(SalesOrder.class))).thenAnswer(inv -> {
            SalesOrder order = inv.getArgument(0);
            order.setId(15L);
            order.setCreatedAt(CREATED_AT);
            return order;
        });
        when(soDetailRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<SalesOrderDetail> details = new ArrayList<>(inv.getArgument(0));
            for (int i = 0; i < details.size(); i++) {
                details.get(i).setId(31L + i);
            }
            return details;
        });

        service = new CheckoutServiceImpl(docNoRepository, productRepository, soRepository, soDetailRepository,
                stockPositionRepository, refundService, cartRedisTemplate, new CartCacheProperties("cart", Duration.ofDays(7)),
                new CheckoutProperties(PAYMENT_TIMEOUT), notificationProducer);

        AccountUtil.setUserLogin(USER);
    }

    @AfterEach
    void tearDown() {
        AccountUtil.clearUserLogin();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static Product product(Long id, String name, String sellPrice) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setSellPrice(new BigDecimal(sellPrice));
        product.setIsActive(true);

        return product;
    }

    private static ProductQuantity stock(Product product, Integer quantity) {
        return new ProductQuantity() {
            @Override
            public Long getProductId() {
                return product.getId();
            }

            @Override
            public Integer getQuantity() {
                return quantity;
            }
        };
    }

    private static CheckoutDetailReq item(Long productId, Integer quantity) {
        CheckoutDetailReq item = new CheckoutDetailReq();
        item.setProductId(productId);
        item.setQuantity(quantity);

        return item;
    }

    private static CheckoutReq request(boolean fromCart, CheckoutDetailReq... items) {
        CheckoutReq req = new CheckoutReq();
        req.setFromCart(fromCart);
        req.setItems(Arrays.asList(items));

        return req;
    }

    private static String key(Long productId) {
        return "cart:" + USER + ":" + productId;
    }

    private void givenProducts(Product... products) {
        when(productRepository.doList(anyList())).thenReturn(Arrays.asList(products));
    }

    private void givenStocks(ProductQuantity... onHand) {
        when(stockPositionRepository.lockQuantities(anyCollection())).thenReturn(Arrays.asList(onHand));
    }

    private void givenReserved(ProductQuantity... reserved) {
        when(soDetailRepository.sumReserved(anyCollection(), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Arrays.asList(reserved));
    }

    private void givenCart(Object... quantities) {
        when(valueOps.multiGet(anyCollection())).thenReturn(Arrays.asList(quantities));
    }

    private SalesOrder givenOrder(SalesStatus status, UUID createdBy) {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        SalesOrder order = new SalesOrder();
        order.setId(15L);
        order.setDocumentNumber(DOC_NO);
        order.setStatus(status);
        order.setGrandTotal(new BigDecimal("100000.00"));
        order.setPaid(BigDecimal.ZERO);
        order.setOutstanding(new BigDecimal("100000.00"));
        order.setCreatedAt(CREATED_AT);
        order.setCreatedBy(createdBy);

        SalesOrderDetail detail = new SalesOrderDetail();
        detail.setId(31L);
        detail.setSalesOrder(order);
        detail.setProduct(kopi);
        detail.setQuantity(2);
        detail.setUnitPrice(kopi.getSellPrice());
        detail.setSubtotal(new BigDecimal("100000.00"));
        order.setSalesOrderDetails(List.of(detail));

        when(soRepository.lockById(15L)).thenReturn(Optional.of(order));
        return order;
    }

    // ------------------------------------------------------------------
    // the happy path
    // ------------------------------------------------------------------

    @Test
    void shouldCheckOutTheCartLinesPickedAtTheCurrentPrice() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        Product gula = product(9L, "Gula Aren 500g", "25000.00");
        givenCart(2, 1);
        givenProducts(kopi, gula);
        givenStocks(stock(kopi, 10), stock(gula, 1));

        Response response = service.doCheckout(request(true, item(7L, 2), item(9L, 1)));

        assertEquals(200, response.getCode());
        assertEquals(ResponseMsg.SUCCESS, response.getStatus());

        CheckoutRes res = assertInstanceOf(CheckoutRes.class, response.getData());
        assertEquals(15L, res.getId());
        assertEquals(DOC_NO, res.getDocumentNumber());
        assertEquals(SalesStatus.PENDING, res.getStatus());
        assertEquals(new BigDecimal("125000.00"), res.getGrandTotal());
        assertEquals(CREATED_AT, res.getCreatedAt());
        assertEquals(CREATED_AT.plusMinutes(60), res.getExpiredAt());

        assertEquals(2, res.getItems().size());
        CheckoutDetailRes first = res.getItems().get(0);
        assertEquals(31L, first.getId());
        assertEquals(7L, first.getProductId());
        assertEquals("Kopi Gayo 200g", first.getProductName());
        assertEquals(2, first.getQuantity());
        assertEquals(new BigDecimal("50000.00"), first.getUnitPrice());
        assertEquals(new BigDecimal("100000.00"), first.getSubtotal());
    }

    @Test
    void shouldRemoveOnlyTheLinesCheckedOutFromTheCart() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenCart(2);
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));

        service.doCheckout(request(true, item(7L, 2)));

        // Whatever else the cart holds stays there for later.
        verify(cartRedisTemplate).delete(List.of(key(7L)));
    }

    @Test
    void shouldLeaveTheCartUntilTheOrderIsCommitted() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenCart(2);
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));
        TransactionSynchronizationManager.initSynchronization();

        service.doCheckout(request(true, item(7L, 2)));

        verify(cartRedisTemplate, never()).delete(anyCollection());

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(cartRedisTemplate).delete(List.of(key(7L)));
    }

    @Test
    void shouldBuyStraightAwayWithoutTouchingTheCart() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));

        Response response = service.doCheckout(request(false, item(7L, 3)));

        CheckoutRes res = assertInstanceOf(CheckoutRes.class, response.getData());
        assertEquals(new BigDecimal("150000.00"), res.getGrandTotal());
        verifyNoInteractions(valueOps);
        verify(cartRedisTemplate, never()).delete(anyCollection());
    }

    @Test
    void shouldSaveAPendingOrderWithItsDetails() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));

        service.doCheckout(request(false, item(7L, 3)));

        ArgumentCaptor<SalesOrder> order = ArgumentCaptor.forClass(SalesOrder.class);
        verify(soRepository).save(order.capture());
        assertEquals(DOC_NO, order.getValue().getDocumentNumber());
        assertEquals(SalesStatus.PENDING, order.getValue().getStatus());
        assertEquals(new BigDecimal("150000.00"), order.getValue().getGrandTotal());

        SalesOrderDetail detail = order.getValue().getSalesOrderDetails().get(0);
        assertEquals(order.getValue(), detail.getSalesOrder());
        assertEquals(kopi, detail.getProduct());
        assertEquals(3, detail.getQuantity());
    }

    // ------------------------------------------------------------------
    // what checkout refuses
    // ------------------------------------------------------------------

    @Test
    void shouldRefuseACheckoutWithNoSignedInUser() {
        AccountUtil.clearUserLogin();

        assertThrows(ForbiddenException.class, () -> service.doCheckout(request(false, item(7L, 1))));

        verifyNoInteractions(productRepository, soRepository, valueOps);
    }

    @Test
    void shouldRefuseTheSameProductTwice() {
        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doCheckout(request(true, item(7L, 1), item(7L, 2))));

        assertEquals("Product with id 7 appears more than once", exc.getMessage());
        verifyNoInteractions(valueOps, soRepository);
    }

    @Test
    void shouldRefuseALineTheCartDoesNotHold() {
        givenCart(2, null);

        NotFoundException exc = assertThrows(NotFoundException.class,
                () -> service.doCheckout(request(true, item(7L, 2), item(9L, 1))));

        assertEquals("Data Product with id 9 not found in cart", exc.getMessage());
        verifyNoInteractions(soRepository);
    }

    @Test
    void shouldRefuseACartThatChangedSinceTheCustomerSawIt() {
        givenCart(5);

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doCheckout(request(true, item(7L, 2))));

        assertEquals("Cart has changed for product with id 7, please refresh", exc.getMessage());
        verifyNoInteractions(soRepository);
    }

    @Test
    void shouldReportAnUnreadableCartAsAServiceError() {
        when(valueOps.multiGet(anyCollection())).thenThrow(new RedisConnectionFailureException("redis is down"));

        ServiceException exc = assertThrows(ServiceException.class,
                () -> service.doCheckout(request(true, item(7L, 2))));

        assertEquals("Failed to read the cart", exc.getMessage());
    }

    @Test
    void shouldRefuseAProductThatDoesNotExist() {
        givenProducts();

        NotFoundException exc = assertThrows(NotFoundException.class,
                () -> service.doCheckout(request(false, item(7L, 1))));

        assertEquals("Data Product with id 7 not found", exc.getMessage());
        verifyNoInteractions(soRepository);
    }

    @Test
    void shouldRefuseAProductNoLongerSold() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        kopi.setIsActive(false);
        givenProducts(kopi);

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doCheckout(request(false, item(7L, 1))));

        assertEquals("Product Kopi Gayo 200g is no longer available", exc.getMessage());
    }

    @Test
    void shouldRefuseMoreThanIsInStock() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 2));

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doCheckout(request(false, item(7L, 3))));

        assertEquals("Quantity (3) cannot be greater than available stock (2) for product Kopi Gayo 200g",
                exc.getMessage());
        verifyNoInteractions(soRepository);
    }

    @Test
    void shouldTreatAProductNeverStockedAsOutOfStock() {
        givenProducts(product(7L, "Kopi Gayo 200g", "50000.00"));
        givenStocks();

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doCheckout(request(false, item(7L, 1))));

        assertEquals("Quantity (1) cannot be greater than available stock (0) for product Kopi Gayo 200g",
                exc.getMessage());
    }

    // ------------------------------------------------------------------
    // stock held by orders not yet deducted
    // ------------------------------------------------------------------

    @Test
    void shouldRefuseTheLastUnitWhenAnotherOrderHoldsIt() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 1));
        givenReserved(stock(kopi, 1));

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doCheckout(request(false, item(7L, 1))));

        assertEquals("Quantity (1) cannot be greater than available stock (0) for product Kopi Gayo 200g",
                exc.getMessage());
        verifyNoInteractions(soRepository);
    }

    @Test
    void shouldSellWhatIsLeftOnceOtherOrdersAreCounted() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));
        givenReserved(stock(kopi, 7));

        Response response = service.doCheckout(request(false, item(7L, 3)));

        assertEquals(200, response.getCode());
    }

    @Test
    void shouldNeverReportANegativeAvailableStock() {
        // Stock taken out by a purchase return can leave less on hand than is promised.
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 2));
        givenReserved(stock(kopi, 5));

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doCheckout(request(false, item(7L, 1))));

        assertEquals("Quantity (1) cannot be greater than available stock (0) for product Kopi Gayo 200g",
                exc.getMessage());
    }

    @Test
    void shouldCountOnlyPendingOrdersInsideThePaymentWindowAndUndeductedPaidOnes() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));
        LocalDateTime before = LocalDateTime.now().minus(PAYMENT_TIMEOUT);

        service.doCheckout(request(false, item(7L, 1)));

        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(soDetailRepository).sumReserved(eq(List.of(7L)), eq(SalesStatus.PENDING.getLabel()),
                eq(SalesStatus.PAID.getLabel()), since.capture());
        LocalDateTime after = LocalDateTime.now().minus(PAYMENT_TIMEOUT);
        assertTrue(!since.getValue().isBefore(before) && !since.getValue().isAfter(after));
    }

    @Test
    void shouldLockTheStockBeforeCountingWhatOtherOrdersHold() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));

        service.doCheckout(request(false, item(7L, 1)));

        // Counted after the lock, the sum includes any order that held the lock before us.
        InOrder order = inOrder(stockPositionRepository, soDetailRepository, soRepository);
        order.verify(stockPositionRepository).lockQuantities(List.of(7L));
        order.verify(soDetailRepository).sumReserved(anyCollection(), anyString(), anyString(), any(LocalDateTime.class));
        order.verify(soRepository).save(any(SalesOrder.class));
    }

    @Test
    void shouldKeepTheOrderWhenTheCartCannotBeCleared() {
        Product kopi = product(7L, "Kopi Gayo 200g", "50000.00");
        givenCart(2);
        givenProducts(kopi);
        givenStocks(stock(kopi, 10));
        when(cartRedisTemplate.delete(anyCollection())).thenThrow(new RedisConnectionFailureException("redis is down"));

        Response response = service.doCheckout(request(true, item(7L, 2)));

        // The order is committed by then; failing the request would hide it from the customer.
        assertEquals(200, response.getCode());
    }

    // ------------------------------------------------------------------
    // cancelling an order
    // ------------------------------------------------------------------

    @Test
    void shouldCancelAPendingOrderOfTheSignedInUser() {
        givenOrder(SalesStatus.PENDING, USER);

        Response response = service.doCancel(15L);

        assertEquals(200, response.getCode());
        assertEquals(ResponseMsg.SUCCESS, response.getStatus());

        CheckoutRes res = assertInstanceOf(CheckoutRes.class, response.getData());
        assertEquals(15L, res.getId());
        assertEquals(DOC_NO, res.getDocumentNumber());
        assertEquals(SalesStatus.CANCELLED, res.getStatus());
        assertEquals(new BigDecimal("100000.00"), res.getGrandTotal());
        assertEquals(CREATED_AT.plusMinutes(60), res.getExpiredAt());
        assertEquals(1, res.getItems().size());
        assertEquals("Kopi Gayo 200g", res.getItems().get(0).getProductName());

        ArgumentCaptor<SalesOrder> saved = ArgumentCaptor.forClass(SalesOrder.class);
        verify(soRepository).save(saved.capture());
        assertEquals(SalesStatus.CANCELLED, saved.getValue().getStatus());
        // Nothing was paid, so nothing is owed back.
        verifyNoInteractions(refundService);
    }

    @Test
    void shouldRefundWhatWasPaidInPartWhenAPendingOrderIsCancelled() {
        SalesOrder order = givenOrder(SalesStatus.PENDING, USER);
        order.setPaid(new BigDecimal("40000.00"));
        order.setOutstanding(new BigDecimal("60000.00"));

        service.doCancel(15L);

        assertEquals(SalesStatus.CANCELLED, order.getStatus());
        verify(refundService).doRefundOrder(order, RefundReason.CANCELLATION);
    }

    @ParameterizedTest
    @EnumSource(value = SalesStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void shouldRefuseToCancelAnOrderThatIsNoLongerPending(SalesStatus status) {
        // A paid order has had its stock taken, so it is cancelled through payment instead.
        givenOrder(status, USER);

        BadRequestException exc = assertThrows(BadRequestException.class, () -> service.doCancel(15L));

        assertEquals("Only a pending order can be cancelled", exc.getMessage());
        verify(soRepository, never()).save(any(SalesOrder.class));
        verifyNoInteractions(refundService);
    }

    @Test
    void shouldCheckTheOwnerBeforeTheStatus() {
        // Someone else's order must not reveal what state it is in.
        givenOrder(SalesStatus.CANCELLED, UUID.fromString("99999999-8888-7777-6666-555555555555"));

        ForbiddenException exc = assertThrows(ForbiddenException.class, () -> service.doCancel(15L));

        assertEquals("You don't have permission to cancel this sales order", exc.getMessage());
        verify(soRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRefuseToCancelAnotherCustomersOrder() {
        givenOrder(SalesStatus.PENDING, UUID.fromString("99999999-8888-7777-6666-555555555555"));

        ForbiddenException exc = assertThrows(ForbiddenException.class, () -> service.doCancel(15L));

        assertEquals("You don't have permission to cancel this sales order", exc.getMessage());
        verify(soRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRefuseToCancelAnOrderWithNoKnownOwner() {
        givenOrder(SalesStatus.PENDING, null);

        ForbiddenException exc = assertThrows(ForbiddenException.class, () -> service.doCancel(15L));

        assertEquals("You don't have permission to cancel this sales order", exc.getMessage());
        verify(soRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRefuseACancelWithNoSignedInUser() {
        givenOrder(SalesStatus.PENDING, USER);
        AccountUtil.clearUserLogin();

        ForbiddenException exc = assertThrows(ForbiddenException.class, () -> service.doCancel(15L));

        assertEquals("You don't have permission to access this resource", exc.getMessage());
        verify(soRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRefuseAnOrderThatDoesNotExist() {
        when(soRepository.lockById(99L)).thenReturn(Optional.empty());

        NotFoundException exc = assertThrows(NotFoundException.class, () -> service.doCancel(99L));

        assertEquals("Data Sales Order with id 99 not found", exc.getMessage());
        verify(soRepository, never()).save(any(SalesOrder.class));
    }

    // ------------------------------------------------------------------
    // expiring an order paid in part
    // ------------------------------------------------------------------

    private SalesOrder givenPartlyPaidOrder(LocalDateTime createdAt) {
        SalesOrder order = givenOrder(SalesStatus.PENDING, USER);
        order.setCreatedAt(createdAt);
        order.setPaid(new BigDecimal("40000.00"));
        order.setOutstanding(new BigDecimal("60000.00"));
        return order;
    }

    @Test
    void shouldExpireAnOrderPastItsWindowAndRefundWhatWasPaid() {
        SalesOrder order = givenPartlyPaidOrder(LocalDateTime.now().minus(PAYMENT_TIMEOUT).minusMinutes(1));

        assertTrue(service.doExpire(15L));

        assertEquals(SalesStatus.EXPIRED, order.getStatus());
        InOrder inOrder = inOrder(soRepository, refundService);
        inOrder.verify(soRepository).save(order);
        inOrder.verify(refundService).doRefundOrder(order, RefundReason.EXPIRED);
        verify(notificationProducer).doCheckoutExpiredAfterCommit(15L);
    }

    @Test
    void shouldLeaveAnOrderStillInsideItsWindow() {
        SalesOrder order = givenPartlyPaidOrder(LocalDateTime.now().minus(PAYMENT_TIMEOUT).plusMinutes(1));

        assertFalse(service.doExpire(15L));

        assertEquals(SalesStatus.PENDING, order.getStatus());
        verify(soRepository, never()).save(any(SalesOrder.class));
        verifyNoInteractions(refundService, notificationProducer);
    }

    @ParameterizedTest
    @EnumSource(value = SalesStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void shouldLeaveAnOrderNoLongerPendingWhenTheJobGetsToIt(SalesStatus status) {
        // Paid in full or cancelled between the job listing it and locking it.
        SalesOrder order = givenPartlyPaidOrder(LocalDateTime.now().minus(PAYMENT_TIMEOUT).minusMinutes(1));
        order.setStatus(status);

        assertFalse(service.doExpire(15L));

        assertEquals(status, order.getStatus());
        verify(soRepository, never()).save(any(SalesOrder.class));
        verifyNoInteractions(refundService, notificationProducer);
    }
}
