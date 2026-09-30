package com.order.api.scheduler;

import com.order.api.configuration.CheckoutConfig.CheckoutProperties;
import com.order.api.producer.NotificationProducer;
import com.order.api.repository.SalesOrderRepository;
import com.order.api.service.CheckoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for expiring orders whose payment window has run out. */
class SalesOrderExpirySchedulerTest {

    private static final Duration PAYMENT_TIMEOUT = Duration.ofMinutes(60);

    private SalesOrderRepository soRepository;
    private CheckoutService checkoutService;
    private NotificationProducer notificationProducer;
    private SalesOrderExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        soRepository = mock(SalesOrderRepository.class);
        checkoutService = mock(CheckoutService.class);
        notificationProducer = mock(NotificationProducer.class);
        scheduler = new SalesOrderExpiryScheduler(soRepository, checkoutService,
                new CheckoutProperties(PAYMENT_TIMEOUT), notificationProducer);
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
    void shouldTellEachCustomerWhoseUnpaidOrderExpired() {
        when(soRepository.expirePending(anyString(), anyString(), any(LocalDateTime.class))).thenReturn(List.of(15L, 16L));

        scheduler.doExpire();

        verify(notificationProducer).doCheckoutExpiredAfterCommit(15L);
        verify(notificationProducer).doCheckoutExpiredAfterCommit(16L);
    }

    @Test
    void shouldTellNobodyWhenNothingExpired() {
        when(soRepository.expirePending(anyString(), anyString(), any(LocalDateTime.class))).thenReturn(List.of());

        scheduler.doExpire();

        verifyNoInteractions(notificationProducer);
    }

    @Test
    void shouldExpireOrdersPaidInPartOneByOneWithTheSameCutoff() {
        when(soRepository.findExpirablePaid(eq("Pending"), any(LocalDateTime.class))).thenReturn(List.of(15L, 16L));

        scheduler.doExpire();

        ArgumentCaptor<LocalDateTime> unpaid = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> paid = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(soRepository).expirePending(anyString(), anyString(), unpaid.capture());
        verify(soRepository).findExpirablePaid(anyString(), paid.capture());
        assertEquals(unpaid.getValue(), paid.getValue());
        verify(checkoutService).doExpire(15L);
        verify(checkoutService).doExpire(16L);
    }

    @Test
    void shouldCarryOnWithTheNextOrderWhenOneFails() {
        when(soRepository.findExpirablePaid(anyString(), any(LocalDateTime.class))).thenReturn(List.of(15L, 16L));
        when(checkoutService.doExpire(15L)).thenThrow(new CannotAcquireLockException("row is locked"));

        assertDoesNotThrow(() -> scheduler.doExpire());

        verify(checkoutService).doExpire(16L);
    }

    @Test
    void shouldStillExpireOrdersPaidInPartWhenTheBulkExpiryFails() {
        when(soRepository.expirePending(anyString(), anyString(), any(LocalDateTime.class)))
                .thenThrow(new DataAccessResourceFailureException("database is down"));
        when(soRepository.findExpirablePaid(anyString(), any(LocalDateTime.class))).thenReturn(List.of(15L));

        assertDoesNotThrow(() -> scheduler.doExpire());

        verify(checkoutService).doExpire(15L);
    }

    @Test
    void shouldSurviveADatabaseFailureSoTheNextRunCanRetry() {
        when(soRepository.expirePending(anyString(), anyString(), any(LocalDateTime.class)))
                .thenThrow(new DataAccessResourceFailureException("database is down"));
        when(soRepository.findExpirablePaid(anyString(), any(LocalDateTime.class)))
                .thenThrow(new DataAccessResourceFailureException("database is down"));

        assertDoesNotThrow(() -> scheduler.doExpire());

        verifyNoInteractions(checkoutService, notificationProducer);
    }
}
