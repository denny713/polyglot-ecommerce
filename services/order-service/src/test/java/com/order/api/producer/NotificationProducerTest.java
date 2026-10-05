package com.order.api.producer;

import com.order.api.model.dto.message.NotificationMsg;
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

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Unit tests for telling the notification service what to mail a customer about. */
class NotificationProducerTest {

    private static final String ORDER_ROUTING_KEY = "notification.order";
    private static final String CART_ROUTING_KEY = "notification.cart";
    private static final UUID USER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private RabbitTemplate template;
    private NotificationProducer producer;

    @BeforeEach
    void setUp() {
        template = mock(RabbitTemplate.class);
        producer = new NotificationProducer(template, ORDER_ROUTING_KEY, CART_ROUTING_KEY);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private record Sent(String routingKey, NotificationMsg msg, MessagePostProcessor postProcessor,
                        CorrelationData correlation) {
    }

    private Sent sent() {
        ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessagePostProcessor> postProcessor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(template).convertAndSend(routingKey.capture(), payload.capture(), postProcessor.capture(),
                correlation.capture());

        return new Sent(routingKey.getValue(), assertInstanceOf(NotificationMsg.class, payload.getValue()),
                postProcessor.getValue(), correlation.getValue());
    }

    private static void assertEnvelope(Sent sent, String eventType) {
        NotificationMsg msg = sent.msg();
        assertEquals(eventType, msg.getEventType());
        assertEquals("order-service", msg.getSource());
        assertNotNull(UUID.fromString(msg.getEventId()));
        assertNotNull(Instant.parse(msg.getOccurredAt()));
        assertEquals(msg.getEventId(), sent.correlation().getId());

        Message message = sent.postProcessor().postProcessMessage(new Message(new byte[0], new MessageProperties()));
        assertEquals(msg.getEventId(), message.getMessageProperties().getMessageId());
        assertEquals(eventType, message.getMessageProperties().getType());
    }

    // ------------------------------------------------------------------
    // order events carry the id of the row and nothing else
    // ------------------------------------------------------------------

    @Test
    void shouldPublishAnExpiredCheckoutWithTheOrderId() {
        producer.doCheckoutExpiredAfterCommit(15L);

        Sent sent = sent();
        assertEquals(ORDER_ROUTING_KEY, sent.routingKey());
        assertEnvelope(sent, "CHECKOUT_EXPIRED");
        assertEquals(15L, sent.msg().getId());
        assertNull(sent.msg().getUserId());
        assertNull(sent.msg().getProductId());
    }

    @Test
    void shouldPublishASucceededPaymentWithThePaymentId() {
        producer.doPaymentSucceededAfterCommit(41L);

        Sent sent = sent();
        assertEquals(ORDER_ROUTING_KEY, sent.routingKey());
        assertEnvelope(sent, "PAYMENT_SUCCEEDED");
        assertEquals(41L, sent.msg().getId());
    }

    @Test
    void shouldPublishACancellationRefundWithTheRefundId() {
        producer.doRefundCancellationAfterCommit(51L);

        Sent sent = sent();
        assertEquals(ORDER_ROUTING_KEY, sent.routingKey());
        assertEnvelope(sent, "REFUND_CANCELLATION");
        assertEquals(51L, sent.msg().getId());
    }

    @Test
    void shouldGiveEveryMessageItsOwnEventId() {
        producer.doPaymentSucceededAfterCommit(41L);
        producer.doPaymentSucceededAfterCommit(41L);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(2)).convertAndSend(anyString(), payload.capture(), any(MessagePostProcessor.class),
                any(CorrelationData.class));
        assertNotEquals(((NotificationMsg) payload.getAllValues().get(0)).getEventId(),
                ((NotificationMsg) payload.getAllValues().get(1)).getEventId());
    }

    @Test
    void shouldWaitForTheCommitInsideATransaction() {
        TransactionSynchronizationManager.initSynchronization();

        producer.doCheckoutExpiredAfterCommit(15L);
        producer.doPaymentSucceededAfterCommit(41L);
        producer.doRefundCancellationAfterCommit(51L);

        // The notification service reads the rows, so it must not hear of them earlier.
        verifyNoInteractions(template);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(template, times(3)).convertAndSend(eq(ORDER_ROUTING_KEY), any(Object.class),
                any(MessagePostProcessor.class), any(CorrelationData.class));
    }

    @Test
    void shouldNotSendWhenTheTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();

        producer.doPaymentSucceededAfterCommit(41L);

        assertFalse(TransactionSynchronizationManager.getSynchronizations().isEmpty());
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(template);
    }

    @Test
    void shouldKeepTheChangeWhenTheBrokerIsDown() {
        doThrow(new AmqpConnectException(new RuntimeException("broker is down")))
                .when(template).convertAndSend(anyString(), any(Object.class), any(MessagePostProcessor.class),
                        any(CorrelationData.class));

        assertDoesNotThrow(() -> producer.doPaymentSucceededAfterCommit(41L));
        assertDoesNotThrow(() -> producer.doCartExpired(USER, 7L));
    }

    // ------------------------------------------------------------------
    // cart
    // ------------------------------------------------------------------

    @Test
    void shouldPublishAnExpiredCartLineWithItsUserAndProduct() {
        producer.doCartExpired(USER, 7L);

        Sent sent = sent();
        assertEquals(CART_ROUTING_KEY, sent.routingKey());
        assertEnvelope(sent, "CART_EXPIRED");
        assertEquals(USER, sent.msg().getUserId());
        assertEquals(7L, sent.msg().getProductId());
        assertNull(sent.msg().getId());
    }

    @Test
    void shouldPublishAnExpiredCartLineStraightAway() {
        // An expiry is not part of any transaction of this service.
        TransactionSynchronizationManager.initSynchronization();

        producer.doCartExpired(USER, 7L);

        verify(template).convertAndSend(eq(CART_ROUTING_KEY), any(Object.class), any(MessagePostProcessor.class),
                any(CorrelationData.class));
    }
}
