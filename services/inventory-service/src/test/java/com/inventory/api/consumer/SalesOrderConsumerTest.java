package com.inventory.api.consumer;

import com.inventory.api.exception.BadRequestException;
import com.inventory.api.exception.NotFoundException;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import com.inventory.api.model.dto.response.Response;
import com.inventory.api.service.SalesOrderService;
import com.inventory.api.util.AccountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the sales order listener.
 * <p>
 * The listener is called directly here rather than through a broker, because what
 * it decides is what matters: which failures are worth redelivering and which are
 * not, and whether the user behind the message reaches the auditing thread local.
 * Message conversion and the queue it binds to belong to
 * {@link com.inventory.api.configuration.BrokerConfig} and are covered there.
 * <p>
 * The exception type is the contract with the broker:
 * {@code AmqpRejectAndDontRequeueException} means "never send this again", so a
 * test asserting it is asserting that a message goes to the dead letter queue
 * instead of being redelivered forever.
 */
class SalesOrderConsumerTest {

    private static final String USER_ID = "3f2a7c14-6f38-4f1e-9a55-7c2f1b9d4e01";

    private SalesOrderService soService;
    private SalesOrderConsumer consumer;

    @BeforeEach
    void setUp() {
        soService = mock(SalesOrderService.class);
        consumer = new SalesOrderConsumer(soService);
    }

    @AfterEach
    void tearDown() {
        AccountUtil.clearUserLogin();
    }

    private static SOSubmitReq message(Long id) {
        SOSubmitReq req = new SOSubmitReq();
        req.setId(id);

        return req;
    }

    @Test
    void shouldHandTheMessageToTheService() {
        SOSubmitReq req = message(90L);
        when(soService.doSubmit(req)).thenReturn(new Response(200, "Success", null));

        consumer.doConsumeSubmit(req, USER_ID);

        verify(soService).doSubmit(req);
    }

    @Test
    void shouldRejectAMessageWithoutAnId() {
        AmqpRejectAndDontRequeueException thrown = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.doConsumeSubmit(message(null), USER_ID));

        assertEquals("Sales order message without an id", thrown.getMessage());
        // Redelivering it would fail the same way, so it must not reach the service.
        verifyNoInteractions(soService);
    }

    @Test
    void shouldRejectAnEmptyBody() {
        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.doConsumeSubmit(null, USER_ID));

        verifyNoInteractions(soService);
    }

    @Test
    void shouldNotRedeliverAnOrderTheStockCannotCover() {
        SOSubmitReq req = message(90L);
        doThrow(new BadRequestException("Sales order quantity (4) cannot be greater than stock quantity (3)"))
                .when(soService).doSubmit(req);

        AmqpRejectAndDontRequeueException thrown = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.doConsumeSubmit(req, USER_ID));

        // The message survives as the reason the message was dead lettered.
        assertEquals("Sales order quantity (4) cannot be greater than stock quantity (3)", thrown.getMessage());
        assertEquals(BadRequestException.class, thrown.getCause().getClass());
    }

    @Test
    void shouldNotRedeliverAnOrderThatDoesNotExist() {
        SOSubmitReq req = message(90L);
        doThrow(new NotFoundException("Data SalesOrder with id 90 not found")).when(soService).doSubmit(req);

        AmqpRejectAndDontRequeueException thrown = assertThrows(AmqpRejectAndDontRequeueException.class,
                () -> consumer.doConsumeSubmit(req, USER_ID));

        assertEquals(NotFoundException.class, thrown.getCause().getClass());
    }

    @Test
    void shouldLetAnUnexpectedFailureThrough() {
        SOSubmitReq req = message(90L);
        IllegalStateException cause = new IllegalStateException("connection closed");
        doThrow(cause).when(soService).doSubmit(req);

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> consumer.doConsumeSubmit(req, USER_ID));

        // Not wrapped: a failure that is not the message's fault keeps its own
        // type so the container, and the log, still show what actually broke.
        assertSame(cause, thrown);
    }

    // ------------------------------------------------------------------
    // auditing
    // ------------------------------------------------------------------

    @Test
    void shouldPublishTheUserFromTheHeaderWhileTheServiceRuns() {
        SOSubmitReq req = message(90L);
        AtomicReference<UUID> seen = new AtomicReference<>();
        when(soService.doSubmit(any())).thenAnswer(call -> {
            seen.set(AccountUtil.getUserLogin());
            return new Response(200, "Success", null);
        });

        consumer.doConsumeSubmit(req, USER_ID);

        // Base stamps created_by from this thread local, and a listener thread
        // never passes through TokenFilter, so the header is the only source.
        assertEquals(UUID.fromString(USER_ID), seen.get());
    }

    @Test
    void shouldClearTheUserWhenTheMessageIsDone() {
        SOSubmitReq req = message(90L);
        when(soService.doSubmit(req)).thenReturn(new Response(200, "Success", null));

        consumer.doConsumeSubmit(req, USER_ID);

        // Left behind, it would be stamped on whatever the pooled thread handles next.
        assertNull(AccountUtil.getUserLogin());
    }

    @Test
    void shouldClearTheUserEvenWhenTheMessageFailed() {
        SOSubmitReq req = message(90L);
        doThrow(new BadRequestException("not enough stock")).when(soService).doSubmit(req);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> consumer.doConsumeSubmit(req, USER_ID));

        assertNull(AccountUtil.getUserLogin());
    }

    @Test
    void shouldAcceptAMessageWithNoUserHeader() {
        SOSubmitReq req = message(90L);
        AtomicReference<UUID> seen = new AtomicReference<>(UUID.randomUUID());
        when(soService.doSubmit(any())).thenAnswer(call -> {
            seen.set(AccountUtil.getUserLogin());
            return new Response(200, "Success", null);
        });

        consumer.doConsumeSubmit(req, null);

        // A message produced by a scheduler has no user behind it; the stock still moves.
        assertNull(seen.get());
        verify(soService).doSubmit(req);
    }

    @Test
    void shouldIgnoreABlankUserHeader() {
        SOSubmitReq req = message(90L);
        AtomicReference<UUID> seen = new AtomicReference<>(UUID.randomUUID());
        when(soService.doSubmit(any())).thenAnswer(call -> {
            seen.set(AccountUtil.getUserLogin());
            return new Response(200, "Success", null);
        });

        consumer.doConsumeSubmit(req, "   ");

        assertNull(seen.get());
    }

    @Test
    void shouldIgnoreAMalformedUserHeaderRatherThanFailTheMessage() {
        SOSubmitReq req = message(90L);
        AtomicReference<UUID> seen = new AtomicReference<>(UUID.randomUUID());
        when(soService.doSubmit(any())).thenAnswer(call -> {
            seen.set(AccountUtil.getUserLogin());
            return new Response(200, "Success", null);
        });

        consumer.doConsumeSubmit(req, "not-a-uuid");

        // Losing the author is worth less than losing the stock movement.
        assertNull(seen.get());
        verify(soService).doSubmit(req);
    }
}
