package com.order.api.scheduler;

import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.repository.SalesOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for expiring orders whose payment window has run out. */
class SalesOrderExpirySchedulerTest {

    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(60);

    private SalesOrderRepository soRepository;
    private SalesOrderExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        soRepository = mock(SalesOrderRepository.class);
        scheduler = new SalesOrderExpiryScheduler(soRepository, new CheckoutProperties(PAYMENT_TIMEOUT));
    }

    @Test
    void shouldExpirePendingOrdersOlderThanThePaymentWindow() {
        LocalDateTime before = LocalDateTime.now().minus(PAYMENT_TIMEOUT);

        scheduler.doExpire();

        LocalDateTime after = LocalDateTime.now().minus(PAYMENT_TIMEOUT);
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        // Stored as labels, the same way the status converter writes them.
        verify(soRepository).expirePending(eq("Pending"), eq("Expired"), cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(before));
        assertFalse(cutoff.getValue().isAfter(after));
    }

    @Test
    void shouldSurviveADatabaseFailureSoTheNextRunCanRetry() {
        when(soRepository.expirePending(anyString(), anyString(), any(LocalDateTime.class)))
                .thenThrow(new DataAccessResourceFailureException("database is down"));

        assertDoesNotThrow(() -> scheduler.doExpire());
    }
}
