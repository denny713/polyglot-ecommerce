package com.order.api.service;

import com.order.api.enums.DocType;
import com.order.api.enums.RefundReason;
import com.order.api.model.entity.Payment;
import com.order.api.model.entity.Refund;
import com.order.api.model.entity.SalesOrder;
import com.order.api.repository.DocumentNumberRepository;
import com.order.api.repository.PaymentRepository;
import com.order.api.repository.RefundRepository;
import com.order.api.service.impl.RefundServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for recording the money a sales order owes back. */
class RefundServiceTest {

    private static final String RF_DOC_NO = "RF20260923001";

    private DocumentNumberRepository docNoRepository;
    private PaymentRepository paymentRepository;
    private RefundRepository refundRepository;

    private RefundServiceImpl service;

    @BeforeEach
    void setUp() {
        docNoRepository = mock(DocumentNumberRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        refundRepository = mock(RefundRepository.class);

        when(docNoRepository.generateDocumentNumber(eq(DocType.REFUND), any(LocalDate.class))).thenReturn(RF_DOC_NO);
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));
        when(refundRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service = new RefundServiceImpl(docNoRepository, paymentRepository, refundRepository);
    }

    private static SalesOrder order() {
        SalesOrder order = new SalesOrder();
        order.setId(15L);
        order.setDocumentNumber("SO20260923001");
        return order;
    }

    private static Payment payment(SalesOrder order, Long id, String account, String applied, String excess) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setSalesOrder(order);
        payment.setBankName("BCA");
        payment.setAccountNumber(account);
        payment.setAccountName("Budi");
        payment.setAppliedAmount(new BigDecimal(applied));
        payment.setExcessAmount(new BigDecimal(excess));
        payment.setAmount(payment.getAppliedAmount().add(payment.getExcessAmount()));
        return payment;
    }

    @Test
    void shouldRefundTheExcessToTheAccountItCameFrom() {
        SalesOrder order = order();
        Payment payment = payment(order, 41L, "1234567890", "60000.00", "40000.00");
        LocalDateTime before = LocalDateTime.now();

        Refund refund = service.doRefundExcess(payment);

        assertEquals(RF_DOC_NO, refund.getDocumentNumber());
        assertEquals(order, refund.getSalesOrder());
        assertEquals(payment, refund.getPayment());
        assertEquals(RefundReason.OVERPAYMENT, refund.getReason());
        assertEquals(new BigDecimal("40000.00"), refund.getAmount());
        assertEquals("BCA", refund.getBankName());
        assertEquals("1234567890", refund.getAccountNumber());
        assertEquals("Budi", refund.getAccountName());
        // Returned the moment it is recorded.
        assertNotNull(refund.getRefundedAt());
        assertFalse(refund.getRefundedAt().isBefore(before));
    }

    @Test
    void shouldRefundEachInstalmentWhatItAppliedToTheOrder() {
        SalesOrder order = order();
        Payment first = payment(order, 41L, "111", "40000.00", "0");
        Payment second = payment(order, 42L, "222", "60000.00", "40000.00");
        when(paymentRepository.findBySalesOrderIdOrderByIdAsc(15L)).thenReturn(List.of(first, second));

        List<Refund> refunds = service.doRefundOrder(order, RefundReason.CANCELLATION);

        assertEquals(2, refunds.size());
        assertEquals(first, refunds.get(0).getPayment());
        assertEquals("111", refunds.get(0).getAccountNumber());
        assertEquals(new BigDecimal("40000.00"), refunds.get(0).getAmount());
        // The excess was refunded when it was paid, so only what was applied goes back now.
        assertEquals(second, refunds.get(1).getPayment());
        assertEquals("222", refunds.get(1).getAccountNumber());
        assertEquals(new BigDecimal("60000.00"), refunds.get(1).getAmount());
        refunds.forEach(refund -> {
            assertEquals(RefundReason.CANCELLATION, refund.getReason());
            assertNotNull(refund.getRefundedAt());
            assertEquals(order, refund.getSalesOrder());
        });
    }

    @Test
    void shouldSkipAPaymentThatAppliedNothing() {
        SalesOrder order = order();
        when(paymentRepository.findBySalesOrderIdOrderByIdAsc(15L))
                .thenReturn(List.of(payment(order, 41L, "111", "0", "50000.00")));

        assertTrue(service.doRefundOrder(order, RefundReason.EXPIRED).isEmpty());

        verify(refundRepository, never()).saveAll(anyList());
    }
}
