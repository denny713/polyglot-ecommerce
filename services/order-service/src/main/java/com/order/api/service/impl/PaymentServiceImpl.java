package com.order.api.service.impl;

import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.constant.ResponseMsg;
import com.order.api.enums.DocType;
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
import com.order.api.service.PaymentService;
import com.order.api.service.RefundService;
import com.order.api.util.AccountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Takes a customer's payment towards a pending sales order. An order may be paid in
 * instalments; the one that clears its outstanding makes it paid, and whatever is
 * sent over the outstanding is refunded straight away.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Jakarta");

    private final DocumentNumberRepository docNoRepository;
    private final SalesOrderRepository soRepository;
    private final PaymentRepository paymentRepository;
    private final RefundService refundService;
    private final SalesOrderProducer soProducer;
    private final CheckoutProperties checkoutProperties;

    /**
     * Applies the amount to the order's outstanding. The order's row stays locked until
     * this commits, so two payments of the same order never both see the outstanding
     * from before the other.
     */
    @Override
    @Transactional
    public Response doPayment(PaymentReq req) {
        UUID userLogin = AccountUtil.requireUserLogin();
        SalesOrder order = soRepository.lockById(req.getSalesOrderId())
                .orElseThrow(() -> new NotFoundException(
                        "Data Sales Order with id " + req.getSalesOrderId() + " not found"));
        if (order.getCreatedBy() == null || !order.getCreatedBy().equals(userLogin)) {
            throw new ForbiddenException("You don't have permission to pay this sales order");
        }

        // Looked up under the lock, so a retry racing the original sees it once it commits.
        Optional<Payment> existing = paymentRepository.findByReference(req.getReference());
        if (existing.isPresent()) {
            return replay(existing.get(), req, order);
        }

        validatePayable(order);

        BigDecimal applied = req.getAmount().min(order.getOutstanding());
        BigDecimal excess = req.getAmount().subtract(applied);

        order.setPaid(order.getPaid().add(applied));
        order.setOutstanding(order.getOutstanding().subtract(applied));
        if (order.getOutstanding().signum() == 0) {
            order.setStatus(SalesStatus.PAID);
        }

        soRepository.save(order);

        Payment payment = paymentRepository.save(buildPayment(order, req, applied, excess));
        if (excess.signum() > 0) {
            refundService.doRefundExcess(payment);
        }

        if (order.getStatus() == SalesStatus.PAID) {
            soProducer.doSubmitAfterCommit(order);
        }

        log.info("Payment {} of {} recorded for sales order {}, outstanding now {}",
                payment.getDocumentNumber(), payment.getAmount(), order.getDocumentNumber(), order.getOutstanding());
        return success(payment, order);
    }

    /**
     * Only a pending order still inside its payment window takes money. The window is
     * checked here too because the expiry job runs only every so often, and checkout
     * stops holding stock for the order the moment the window passes.
     */
    private void validatePayable(SalesOrder order) {
        if (order.getStatus() != SalesStatus.PENDING) {
            throw new BadRequestException("Only a pending order can be paid");
        }

        LocalDateTime deadline = order.getCreatedAt().plus(checkoutProperties.paymentTimeout());
        if (!LocalDateTime.now().isBefore(deadline)) {
            throw new BadRequestException("The payment window of sales order "
                    + order.getDocumentNumber() + " has passed");
        }
    }

    /**
     * A reference sent again with the same order, amount and account is the same
     * payment retried, so it gets the payment already recorded rather than being
     * charged twice. Anything else under a used reference, such as the next instalment
     * sent with the reference of the previous one, is refused so it is not mistaken
     * for a retry.
     */
    private Response replay(Payment payment, PaymentReq req, SalesOrder order) {
        if (!isSamePayment(payment, req, order)) {
            throw new BadRequestException("Reference " + payment.getReference() + " has already been used by payment "
                    + payment.getDocumentNumber() + ", each payment needs its own reference");
        }

        log.info("Payment {} replayed for reference {}", payment.getDocumentNumber(), payment.getReference());
        return success(payment, order);
    }

    private static boolean isSamePayment(Payment payment, PaymentReq req, SalesOrder order) {
        return payment.getSalesOrder() != null
                && order.getId().equals(payment.getSalesOrder().getId())
                && payment.getAmount() != null && payment.getAmount().compareTo(req.getAmount()) == 0
                && payment.getMethod() == req.getMethod()
                && Objects.equals(payment.getBankName(), req.getBankName())
                && Objects.equals(payment.getAccountNumber(), req.getAccountNumber())
                && Objects.equals(payment.getAccountName(), req.getAccountName());
    }

    private Payment buildPayment(SalesOrder order, PaymentReq req, BigDecimal applied, BigDecimal excess) {
        Payment payment = new Payment();

        payment.setDocumentNumber(docNoRepository.generateDocumentNumber(DocType.PAYMENT, LocalDate.now(ZONE)));
        payment.setSalesOrder(order);
        payment.setReference(req.getReference());
        payment.setMethod(req.getMethod());
        payment.setBankName(req.getBankName());
        payment.setAccountNumber(req.getAccountNumber());
        payment.setAccountName(req.getAccountName());
        payment.setAmount(req.getAmount());
        payment.setAppliedAmount(applied);
        payment.setExcessAmount(excess);
        payment.setPaidAt(LocalDateTime.now());

        return payment;
    }

    private Response success(Payment payment, SalesOrder order) {
        PaymentRes res = new PaymentRes(payment.getId(), order.getDocumentNumber(), payment.getDocumentNumber(),
                payment.getReference(), payment.getMethod(), payment.getBankName(), payment.getAccountNumber(),
                payment.getAccountName(), payment.getAmount(), payment.getAppliedAmount(),
                payment.getExcessAmount(), payment.getPaidAt(), order.getStatus(), order.getPaid(),
                order.getOutstanding());

        return new Response(200, ResponseMsg.SUCCESS, res);
    }
}
