package com.order.api.scheduler;

import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.enums.SalesStatus;
import com.order.api.repository.SalesOrderRepository;
import com.order.api.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Marks pending orders whose payment window has run out as expired. Checkout already
 * stops counting such an order against stock once the window passes, and payment
 * refuses it, so this job only brings the status in line and refunds whatever was
 * paid in part; running it late, twice, or on several instances at once changes
 * nothing but the moment the status flips.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderExpiryScheduler {

    private final SalesOrderRepository soRepository;
    private final CheckoutService checkoutService;
    private final CheckoutProperties checkoutProperties;

    @Scheduled(fixedDelayString = "${checkout.expiry-interval}")
    public void doExpire() {
        LocalDateTime cutoff = LocalDateTime.now().minus(checkoutProperties.paymentTimeout());

        expireUnpaid(cutoff);
        expirePaidInPart(cutoff);
    }

    /** Orders nothing was paid towards owe no refund, so they go in one statement. */
    private void expireUnpaid(LocalDateTime cutoff) {
        try {
            int expired = soRepository.expirePending(
                    SalesStatus.PENDING.getLabel(), SalesStatus.EXPIRED.getLabel(), cutoff);
            if (expired > 0) {
                log.info("{} pending sales order(s) checked out at or before {} expired", expired, cutoff);
            }
        } catch (DataAccessException e) {
            // The next run picks the same orders up again.
            log.error("Unable to expire pending sales orders checked out at or before {}", cutoff, e);
        }
    }

    /**
     * Orders paid in part are expired one by one, each together with its refunds, so
     * one that fails leaves the others expired and is simply tried again next run.
     */
    private void expirePaidInPart(LocalDateTime cutoff) {
        List<Long> ids;
        try {
            ids = soRepository.findExpirablePaid(SalesStatus.PENDING.getLabel(), cutoff);
        } catch (DataAccessException e) {
            log.error("Unable to list partly paid sales orders checked out at or before {}", cutoff, e);
            return;
        }

        for (Long id : ids) {
            try {
                checkoutService.doExpire(id);
            } catch (RuntimeException e) {
                log.error("Unable to expire partly paid sales order {}", id, e);
            }
        }
    }
}
