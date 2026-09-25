package com.order.api.scheduler;

import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.enums.SalesStatus;
import com.order.api.repository.SalesOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Marks pending orders whose payment window has run out as expired. Checkout already
 * stops counting such an order against stock once the window passes, so this job
 * only brings the status in line; running it late, twice, or on several instances at
 * once changes nothing but the moment the status flips.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderExpiryScheduler {

    private final SalesOrderRepository soRepository;
    private final CheckoutProperties checkoutProperties;

    @Scheduled(fixedDelayString = "${checkout.expiry-interval}")
    public void doExpire() {
        LocalDateTime cutoff = LocalDateTime.now().minus(checkoutProperties.paymentTimeout());

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
}
