package com.order.api.service;

import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.constant.ResponseMsg;
import com.order.api.enums.DocType;
import com.order.api.enums.PaymentMethod;
import com.order.api.enums.SalesStatus;
import com.order.api.exception.BadRequestException;
import com.order.api.exception.ForbiddenException;
import com.order.api.exception.NotFoundException;
import com.order.api.model.dto.request.payment.PaymentReq;
import com.order.api.model.dto.response.Response;
import com.order.api.model.dto.response.payment.PaymentRes;
import com.order.api.model.entity.Payment;
import com.order.api.model.entity.SalesOrder;
import com.order.api.producer.SalesOrderProducer;
import com.order.api.repository.DocumentNumberRepository;
import com.order.api.repository.PaymentRepository;
import com.order.api.repository.SalesOrderRepository;
import com.order.api.service.impl.PaymentServiceImpl;
import com.order.api.util.AccountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for paying towards a pending sales order. */
class PaymentServiceTest {

    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID OTHER = UUID.fromString("99999999-8888-7777-6666-555555555555");
    private static final String SO_DOC_NO = "SO20260923001";
    private static final String PY_DOC_NO = "PY20260923001";
    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(60);

    private DocumentNumberRepository docNoRepository;
    private SalesOrderRepository soRepository;
    private PaymentRepository paymentRepository;
    private RefundService refundService;
    private SalesOrderProducer soProducer;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        docNoRepository = mock(DocumentNumberRepository.class);
        soRepository = mock(SalesOrderRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        refundService = mock(RefundService.class);
        soProducer = mock(SalesOrderProducer.class);

        when(docNoRepository.generateDocumentNumber(eq(DocType.PAYMENT), any(LocalDate.class))).thenReturn(PY_DOC_NO);
        when(paymentRepository.findByReference(anyString())).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment payment = inv.getArgument(0);
            payment.setId(41L);
            return payment;
        });

        service = new PaymentServiceImpl(docNoRepository, soRepository, paymentRepository, refundService,
                soProducer, new CheckoutProperties(PAYMENT_TIMEOUT));

        AccountUtil.setUserLogin(USER);
    }

    @AfterEach
    void tearDown() {
        AccountUtil.clearUserLogin();
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private SalesOrder givenOrder(String paid, String outstanding) {
        SalesOrder order = new SalesOrder();
        order.setId(15L);
        order.setDocumentNumber(SO_DOC_NO);
        order.setStatus(SalesStatus.PENDING);
        order.setGrandTotal(new BigDecimal("100000.00"));
        order.setPaid(new BigDecimal(paid));
        order.setOutstanding(new BigDecimal(outstanding));
        order.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        order.setCreatedBy(USER);

        when(soRepository.lockById(15L)).thenReturn(Optional.of(order));
        return order;
    }

    private static PaymentReq request(String amount) {
        return request(amount, "TRX-001");
    }

    private static PaymentReq request(String amount, String reference) {
        PaymentReq req = new PaymentReq();
        req.setSalesOrderId(15L);
        req.setReference(reference);
        req.setMethod(PaymentMethod.TRF);
        req.setBankName("BCA");
        req.setAccountNumber("1234567890");
        req.setAccountName("Budi");
        req.setAmount(new BigDecimal(amount));

        return req;
    }

    /** A payment recorded earlier from the same account as {@link #request}. */
    private static Payment recorded(SalesOrder order, String amount) {
        Payment payment = new Payment();
        payment.setId(40L);
        payment.setDocumentNumber("PY20260923000");
        payment.setSalesOrder(order);
        payment.setReference("TRX-001");
        payment.setMethod(PaymentMethod.TRF);
        payment.setBankName("BCA");
        payment.setAccountNumber("1234567890");
        payment.setAccountName("Budi");
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }

    private Payment savedPayment() {
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        return saved.getValue();
    }

    // ------------------------------------------------------------------
    // applying the amount
    // ------------------------------------------------------------------

    @Test
    void shouldMarkTheOrderPaidWhenTheAmountClearsTheOutstanding() {
        SalesOrder order = givenOrder("0", "100000.00");

        Response response = service.doPayment(request("100000.00"));

        assertEquals(200, response.getCode());
        assertEquals(ResponseMsg.SUCCESS, response.getStatus());
        PaymentRes res = assertInstanceOf(PaymentRes.class, response.getData());
        assertEquals(41L, res.getId());
        assertEquals(SO_DOC_NO, res.getSalesOrderDocNo());
        assertEquals(PY_DOC_NO, res.getPaymentDocNo());
        assertEquals(SalesStatus.PAID, res.getSalesOrderStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(res.getOutstanding()));
        assertNotNull(res.getPaidAt());

        assertEquals(SalesStatus.PAID, order.getStatus());
        assertEquals(new BigDecimal("100000.00"), order.getPaid());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getOutstanding()));

        Payment payment = savedPayment();
        assertEquals(order, payment.getSalesOrder());
        assertEquals("TRX-001", payment.getReference());
        assertEquals(PaymentMethod.TRF, payment.getMethod());
        assertEquals("1234567890", payment.getAccountNumber());
        assertEquals(new BigDecimal("100000.00"), payment.getAmount());
        assertEquals(new BigDecimal("100000.00"), payment.getAppliedAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(payment.getExcessAmount()));

        verify(soRepository).save(order);
        verifyNoInteractions(refundService);
        verify(soProducer).doSubmitAfterCommit(order);
    }

    @Test
    void shouldKeepTheOrderPendingWhenTheAmountFallsShort() {
        SalesOrder order = givenOrder("0", "100000.00");

        Response response = service.doPayment(request("40000.00"));

        PaymentRes res = assertInstanceOf(PaymentRes.class, response.getData());
        assertEquals(SalesStatus.PENDING, res.getSalesOrderStatus());
        assertEquals(new BigDecimal("60000.00"), res.getOutstanding());

        assertEquals(SalesStatus.PENDING, order.getStatus());
        assertEquals(new BigDecimal("40000.00"), order.getPaid());
        assertEquals(new BigDecimal("60000.00"), order.getOutstanding());
        assertEquals(new BigDecimal("40000.00"), savedPayment().getAppliedAmount());

        verifyNoInteractions(refundService, soProducer);
    }

    @Test
    void shouldMarkTheOrderPaidWhenAnInstalmentClearsWhatIsLeft() {
        // Not the grand total, but what is still outstanding after an earlier instalment.
        SalesOrder order = givenOrder("40000.00", "60000.00");

        service.doPayment(request("60000.00"));

        assertEquals(SalesStatus.PAID, order.getStatus());
        assertEquals(new BigDecimal("100000.00"), order.getPaid());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getOutstanding()));
        verifyNoInteractions(refundService);
        verify(soProducer).doSubmitAfterCommit(order);
    }

    @Test
    void shouldRefundWhatIsPaidOverTheOutstanding() {
        SalesOrder order = givenOrder("40000.00", "60000.00");

        Response response = service.doPayment(request("100000.00"));

        PaymentRes res = assertInstanceOf(PaymentRes.class, response.getData());
        assertEquals(new BigDecimal("60000.00"), res.getAppliedAmount());
        assertEquals(new BigDecimal("40000.00"), res.getExcessAmount());

        assertEquals(SalesStatus.PAID, order.getStatus());
        assertEquals(new BigDecimal("100000.00"), order.getPaid());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getOutstanding()));

        Payment payment = savedPayment();
        assertEquals(new BigDecimal("100000.00"), payment.getAmount());
        assertEquals(new BigDecimal("60000.00"), payment.getAppliedAmount());
        assertEquals(new BigDecimal("40000.00"), payment.getExcessAmount());

        // The refund points at the payment, so the payment must be saved first.
        InOrder inOrder = inOrder(paymentRepository, refundService);
        inOrder.verify(paymentRepository).save(payment);
        inOrder.verify(refundService).doRefundExcess(payment);
        verify(soProducer).doSubmitAfterCommit(order);
    }

    // ------------------------------------------------------------------
    // replaying a reference
    // ------------------------------------------------------------------

    @Test
    void shouldReturnThePaymentAlreadyRecordedWhenTheSamePaymentIsRetried() {
        SalesOrder order = givenOrder("40000.00", "60000.00");
        when(paymentRepository.findByReference("TRX-001")).thenReturn(Optional.of(recorded(order, "40000.00")));

        // Same amount at another scale is still the same amount.
        Response response = service.doPayment(request("40000"));

        PaymentRes res = assertInstanceOf(PaymentRes.class, response.getData());
        assertEquals(40L, res.getId());
        assertEquals("PY20260923000", res.getPaymentDocNo());
        assertEquals(new BigDecimal("40000.00"), order.getPaid());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(soRepository, never()).save(any(SalesOrder.class));
        verifyNoInteractions(refundService, soProducer);
    }

    @Test
    void shouldReplayEvenOnceTheOrderIsPaid() {
        // The retry of the payment that cleared it must not be refused as a second payment.
        SalesOrder order = givenOrder("100000.00", "0");
        order.setStatus(SalesStatus.PAID);
        when(paymentRepository.findByReference("TRX-001")).thenReturn(Optional.of(recorded(order, "100000.00")));

        Response response = service.doPayment(request("100000.00"));

        assertEquals(40L, assertInstanceOf(PaymentRes.class, response.getData()).getId());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldRefuseTheNextInstalmentSentWithThePreviousReference() {
        // Paid 40000 under TRX-001, then the remaining 60000 under TRX-001 again: this
        // is a new payment, and replaying the first one would silently drop it.
        SalesOrder order = givenOrder("40000.00", "60000.00");
        when(paymentRepository.findByReference("TRX-001")).thenReturn(Optional.of(recorded(order, "40000.00")));

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doPayment(request("60000.00")));

        assertEquals("Reference TRX-001 has already been used by payment PY20260923000, "
                + "each payment needs its own reference", exc.getMessage());
        assertEquals(new BigDecimal("40000.00"), order.getPaid());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(soRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRefuseAReusedReferenceFromAnotherAccount() {
        SalesOrder order = givenOrder("40000.00", "60000.00");
        when(paymentRepository.findByReference("TRX-001")).thenReturn(Optional.of(recorded(order, "40000.00")));
        PaymentReq req = request("40000.00");
        req.setAccountNumber("9999999999");

        assertThrows(BadRequestException.class, () -> service.doPayment(req));

        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldRecordTheNextInstalmentUnderANewReference() {
        SalesOrder order = givenOrder("40000.00", "60000.00");
        when(paymentRepository.findByReference("TRX-001")).thenReturn(Optional.of(recorded(order, "40000.00")));

        Response response = service.doPayment(request("60000.00", "TRX-002"));

        PaymentRes res = assertInstanceOf(PaymentRes.class, response.getData());
        assertEquals(SalesStatus.PAID, res.getSalesOrderStatus());
        assertEquals("TRX-002", savedPayment().getReference());
        assertEquals(SalesStatus.PAID, order.getStatus());
        assertEquals(new BigDecimal("100000.00"), order.getPaid());
        verify(soProducer).doSubmitAfterCommit(order);
    }

    @Test
    void shouldRefuseAReferenceUsedForAnotherOrder() {
        givenOrder("0", "100000.00");
        SalesOrder another = new SalesOrder();
        another.setId(16L);
        when(paymentRepository.findByReference("TRX-001")).thenReturn(Optional.of(recorded(another, "100000.00")));

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doPayment(request("100000.00")));

        assertEquals("Reference TRX-001 has already been used by payment PY20260923000, "
                + "each payment needs its own reference", exc.getMessage());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ------------------------------------------------------------------
    // refusals
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = SalesStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void shouldRefuseAnOrderThatIsNoLongerPending(SalesStatus status) {
        givenOrder("0", "100000.00").setStatus(status);

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doPayment(request("100000.00")));

        assertEquals("Only a pending order can be paid", exc.getMessage());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(soRepository, never()).save(any(SalesOrder.class));
    }

    @Test
    void shouldRefuseAnOrderPastItsPaymentWindowBeforeTheJobExpiresIt() {
        givenOrder("0", "100000.00").setCreatedAt(LocalDateTime.now().minus(PAYMENT_TIMEOUT).minusSeconds(1));

        BadRequestException exc = assertThrows(BadRequestException.class,
                () -> service.doPayment(request("100000.00")));

        assertEquals("The payment window of sales order " + SO_DOC_NO + " has passed", exc.getMessage());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldRefuseAnotherCustomersOrder() {
        givenOrder("0", "100000.00").setCreatedBy(OTHER);

        ForbiddenException exc = assertThrows(ForbiddenException.class,
                () -> service.doPayment(request("100000.00")));

        assertEquals("You don't have permission to pay this sales order", exc.getMessage());
        verify(paymentRepository, never()).findByReference(anyString());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void shouldCheckTheOwnerBeforeTheStatus() {
        SalesOrder order = givenOrder("0", "100000.00");
        order.setStatus(SalesStatus.CANCELLED);
        order.setCreatedBy(OTHER);

        assertThrows(ForbiddenException.class, () -> service.doPayment(request("100000.00")));
    }

    @Test
    void shouldRefuseAPaymentWithNoSignedInUser() {
        givenOrder("0", "100000.00");
        AccountUtil.clearUserLogin();

        ForbiddenException exc = assertThrows(ForbiddenException.class,
                () -> service.doPayment(request("100000.00")));

        assertEquals("You don't have permission to access this resource", exc.getMessage());
        verifyNoInteractions(soRepository);
    }

    @Test
    void shouldRefuseAnOrderThatDoesNotExist() {
        when(soRepository.lockById(15L)).thenReturn(Optional.empty());

        NotFoundException exc = assertThrows(NotFoundException.class,
                () -> service.doPayment(request("100000.00")));

        assertEquals("Data Sales Order with id 15 not found", exc.getMessage());
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
