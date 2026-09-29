package com.order.api.producer;

import com.order.api.model.dto.message.SOCancelMsg;
import com.order.api.model.dto.message.SOSubmitMsg;
import com.order.api.model.entity.SalesOrder;
import com.order.api.util.AccountUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Unit tests for telling the inventory service that an order is paid or cancelled. */
class SalesOrderProducerTest {

    private static final String CANCEL_ROUTING_KEY = "sales.order.cancelled";
    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private RabbitTemplate template;
    private SalesOrderProducer producer;

    @BeforeEach
    void setUp() {
        template = mock(RabbitTemplate.class);
        producer = new SalesOrderProducer(template, CANCEL_ROUTING_KEY);
        AccountUtil.setUserLogin(USER);
    }

    @AfterEach
    void tearDown() {
        AccountUtil.clearUserLogin();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static SalesOrder order() {
        SalesOrder order = new SalesOrder();
        order.setId(15L);
        order.setDocumentNumber("SO20260923001");
        return order;
    }

    @Test
    void shouldPublishTheOrderIdWithTheUserWhoPaid() {
        producer.doSubmitAfterCommit(order());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(payload.capture(), postProcessor.capture(), correlation.capture());

        assertEquals(15L, assertInstanceOf(SOSubmitMsg.class, payload.getValue()).getId());
        assertEquals("SO20260923001", correlation.getValue().getId());

        Message message = postProcessor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals(USER.toString(), message.getMessageProperties().getHeader("X-User-Id"));
    }

    @Test
    void shouldWaitForTheCommitInsideATransaction() {
        TransactionSynchronizationManager.initSynchronization();

        producer.doSubmitAfterCommit(order());

        // The consumer reads the order from the database, so it must not hear of it earlier.
        verifyNoInteractions(template);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template).convertAndSend(any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void shouldNotSendWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        producer.doSubmitAfterCommit(order());

        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(template);
    }

    @Test
    void shouldKeepThePaymentWhenTheBrokerIsDown() {
        doThrow(new AmqpConnectException(new RuntimeException("broker is down")))
                .when(template).convertAndSend(any(Object.class), any(MessagePostProcessor.class), any(CorrelationData.class));

        assertDoesNotThrow(() -> producer.doSubmitAfterCommit(order()));
    }

    // ------------------------------------------------------------------
    // cancel
    // ------------------------------------------------------------------

    @Test
    void shouldPublishACancelOnTheCancelRoutingKey() {
        producer.doCancelAfterCommit(order());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(eq(CANCEL_ROUTING_KEY), payload.capture(), postProcessor.capture(),
                correlation.capture());
        // Not the submit routing key the template defaults to.
        verify(template, never()).convertAndSend(any(Object.class), any(MessagePostProcessor.class),
                any(CorrelationData.class));

        assertEquals(15L, assertInstanceOf(SOCancelMsg.class, payload.getValue()).getId());
        assertEquals("SO20260923001", correlation.getValue().getId());
        Message message = postProcessor.getValue().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals(USER.toString(), message.getMessageProperties().getHeader("X-User-Id"));
    }

    @Test
    void shouldWaitForTheCommitBeforeCancelling() {
        TransactionSynchronizationManager.initSynchronization();

        producer.doCancelAfterCommit(order());

        verifyNoInteractions(template);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template).convertAndSend(eq(CANCEL_ROUTING_KEY), any(Object.class), any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }

    @Test
    void shouldKeepTheCancelWhenTheBrokerIsDown() {
        doThrow(new AmqpConnectException(new RuntimeException("broker is down")))
                .when(template).convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class),
                        any(CorrelationData.class));

        assertDoesNotThrow(() -> producer.doCancelAfterCommit(order()));
    }
}
