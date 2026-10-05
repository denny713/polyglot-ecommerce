package com.order.api.producer;

import com.order.api.configuration.BrokerConfig;
import com.order.api.model.dto.message.SOCancelMsg;
import com.order.api.model.dto.message.SOSubmitMsg;
import com.order.api.model.entity.SalesOrder;
import com.order.api.util.AccountUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Tells the inventory service about sales orders it has to act on. Every message goes
 * out only once the change behind it commits: the consumer reads the order from the
 * database, so it must not be told before the order is there to read.
 */
@Slf4j
@Component
public class SalesOrderProducer {

    private final RabbitTemplate salesRabbitTemplate;
    private final String cancelRoutingKey;

    public SalesOrderProducer(RabbitTemplate salesRabbitTemplate,
                              @Value("${rabbitmq.routing-key.so-cancel}") String cancelRoutingKey) {
        this.salesRabbitTemplate = salesRabbitTemplate;
        this.cancelRoutingKey = cancelRoutingKey;
    }

    /** Asks the inventory service to deduct the stock of a fully paid order. */
    public void doSubmitAfterCommit(SalesOrder order) {
        String docNo = order.getDocumentNumber();
        UUID userLogin = AccountUtil.getUserLogin();
        Object payload = new SOSubmitMsg(order.getId());

        // The template's default routing key is the submit one.
        afterCommit(() -> send("submit", docNo, userLogin,
                correlation -> salesRabbitTemplate.convertAndSend(payload,
                        message -> withUser(message, userLogin), correlation)));
    }

    /** Asks the inventory service to put back the stock of a cancelled paid order. */
    public void doCancelAfterCommit(SalesOrder order) {
        String docNo = order.getDocumentNumber();
        UUID userLogin = AccountUtil.getUserLogin();
        Object payload = new SOCancelMsg(order.getId());

        afterCommit(() -> send("cancel", docNo, userLogin,
                correlation -> salesRabbitTemplate.convertAndSend(cancelRoutingKey, payload,
                        message -> withUser(message, userLogin), correlation)));
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

    private static Message withUser(Message message, UUID userLogin) {
        if (userLogin != null) {
            message.getMessageProperties().setHeader(BrokerConfig.USER_HEADER, userLogin.toString());
        }
        return message;
    }

    private void send(String action, String docNo, UUID userLogin, Sender sender) {
        try {
            sender.send(new CorrelationData(docNo));
            log.info("{} message for sales order {} published", action, docNo);
        } catch (AmqpException e) {
            // The change is already committed and must not be undone for this; the
            // inventory service stays behind until someone resends the message.
            log.error("Unable to publish the {} message for sales order {} (user {})", action, docNo, userLogin, e);
        }
    }

    @FunctionalInterface
    private interface Sender {
        void send(CorrelationData correlation);
    }
}
