package com.order.api.service.impl;

import com.order.api.enums.DocType;
import com.order.api.enums.RefundReason;
import com.order.api.model.entity.Payment;
import com.order.api.model.entity.Refund;
import com.order.api.model.entity.SalesOrder;
import com.order.api.repository.DocumentNumberRepository;
import com.order.api.repository.PaymentRepository;
import com.order.api.repository.RefundRepository;
import com.order.api.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Returns the money a sales order owes back to its customer. Every refund goes back
 * to the account of the payment it came from and counts as returned once recorded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    private final DocumentNumberRepository docNoRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;

    /**
     * Refunds what a payment sent over the outstanding of its order. The caller only
     * asks when there is an excess.
     */
    @Override
    public Refund doRefundExcess(Payment payment) {
        Refund refund = refundRepository.save(build(payment, payment.getExcessAmount(), RefundReason.OVERPAYMENT));

        log.info("Refund {} of {} recorded for the excess of payment {}",
                refund.getDocumentNumber(), refund.getAmount(), payment.getDocumentNumber());
        return refund;
    }

    /**
     * Refunds everything applied to an order, one refund per payment, so each
     * instalment goes back where it came from. Excesses were refunded when they were
     * paid and are not counted again. The caller holds the order's row lock.
     */
    @Override
    public List<Refund> doRefundOrder(SalesOrder order, RefundReason reason) {
        List<Refund> refunds = paymentRepository.findBySalesOrderIdOrderByIdAsc(order.getId()).stream()
                .filter(payment -> payment.getAppliedAmount().signum() > 0)
                .map(payment -> build(payment, payment.getAppliedAmount(), reason))
                .toList();
        if (refunds.isEmpty()) {
            return List.of();
        }

        List<Refund> saved = refundRepository.saveAll(refunds);
        log.info("{} refund(s) totalling {} recorded for sales order {} ({})", saved.size(),
                saved.stream().map(Refund::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                order.getDocumentNumber(), reason);
        return saved;
    }

    private Refund build(Payment payment, BigDecimal amount, RefundReason reason) {
        Refund refund = new Refund();

        refund.setDocumentNumber(docNoRepository.generateDocumentNumber(DocType.REFUND, LocalDate.now(ZONE)));
        refund.setSalesOrder(payment.getSalesOrder());
        refund.setPayment(payment);
        refund.setReason(reason);
        refund.setAmount(amount);
        refund.setBankName(payment.getBankName());
        refund.setAccountNumber(payment.getAccountNumber());
        refund.setAccountName(payment.getAccountName());
        refund.setRefundedAt(LocalDateTime.now());

        return refund;
    }
}
