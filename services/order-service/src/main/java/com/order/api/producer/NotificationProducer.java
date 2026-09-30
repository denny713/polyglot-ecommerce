package com.order.api.producer;

import com.order.api.enums.NotificationType;
import com.order.api.model.dto.message.NotificationMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

/**
 * Tells the notification service what to mail a customer about. Only ids go out: the
 * notification service reads the rest from the database, so every message is sent
 * once the change behind it commits.
 */
@Slf4j
@Component
public class NotificationProducer {

    static final String SOURCE = "order-service";

    private final RabbitTemplate notificationRabbitTemplate;
    private final String orderRoutingKey;
    private final String cartRoutingKey;

    public NotificationProducer(RabbitTemplate notificationRabbitTemplate,
                                @Value("${rabbitmq.routing-key.notification-order}") String orderRoutingKey,
                                @Value("${rabbitmq.routing-key.notification-cart}") String cartRoutingKey) {
        this.notificationRabbitTemplate = notificationRabbitTemplate;
        this.orderRoutingKey = orderRoutingKey;
        this.cartRoutingKey = cartRoutingKey;
    }

    /** A pending order ran out of time to be paid. */
    public void doCheckoutExpiredAfterCommit(Long salesOrderId) {
        afterCommit(() -> send(orderRoutingKey, message(NotificationType.CHECKOUT_EXPIRED, salesOrderId)));
    }

    /** A payment was recorded, whether or not it cleared the order. */
    public void doPaymentSucceededAfterCommit(Long paymentId) {
        afterCommit(() -> send(orderRoutingKey, message(NotificationType.PAYMENT_SUCCEEDED, paymentId)));
    }

    /** Money went back to the customer for an order they cancelled. */
    public void doRefundCancellationAfterCommit(Long refundId) {
        afterCommit(() -> send(orderRoutingKey, message(NotificationType.REFUND_CANCELLATION, refundId)));
    }

    /**
     * A cart line expired in Redis. There is no transaction behind it, and the line
     * itself is already gone, so only whose it was and what it held are sent.
     */
    public void doCartExpired(UUID userId, Long productId) {
        NotificationMsg msg = message(NotificationType.CART_EXPIRED, null);
        msg.setUserId(userId);
        msg.setProductId(productId);

        send(cartRoutingKey, msg);
    }

    private static NotificationMsg message(NotificationType type, Long id) {
        return new NotificationMsg(UUID.randomUUID().toString(), type.name(), Instant.now().toString(), SOURCE,
                id, null, null);
    }

    private static void afterCommit(Runnable send) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }

    private void send(String routingKey, NotificationMsg msg) {
        try {
            notificationRabbitTemplate.convertAndSend(routingKey, msg, message -> {
                MessageProperties properties = message.getMessageProperties();
                properties.setMessageId(msg.getEventId());
                properties.setType(msg.getEventType());
                return message;
            }, new CorrelationData(msg.getEventId()));
            log.info("{} notification {} published", msg.getEventType(), msg.getEventId());
        } catch (AmqpException e) {
            // The change is already committed and must not be undone for a missing email.
            log.error("Unable to publish the {} notification (id {}, user {}, product {})",
                    msg.getEventType(), msg.getId(), msg.getUserId(), msg.getProductId(), e);
        }
    }
}
