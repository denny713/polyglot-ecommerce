package com.order.api.producer;

import com.order.api.configuration.BrokerConfig;
import com.order.api.model.dto.message.SOSubmitMsg;
import com.order.api.model.entity.SalesOrder;
import com.order.api.util.AccountUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Tells the inventory service about sales orders it has to act on. */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesOrderProducer {

    private final RabbitTemplate salesRabbitTemplate;

    /**
     * Asks the inventory service to deduct the stock of a fully paid order, once the
     * payment commits: the consumer reads the order from the database, so it must not
     * be told before the order is there to read.
     */
    public void doSubmitAfterCommit(SalesOrder order) {
        Long id = order.getId();
        String docNo = order.getDocumentNumber();
        UUID userLogin = AccountUtil.getUserLogin();
        Runnable send = () -> send(id, docNo, userLogin);

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

    private void send(Long id, String docNo, UUID userLogin) {
        try {
            salesRabbitTemplate.convertAndSend(new SOSubmitMsg(id), message -> {
                if (userLogin != null) {
                    message.getMessageProperties().setHeader(BrokerConfig.USER_HEADER, userLogin.toString());
                }
                return message;
            }, new CorrelationData(docNo));
            log.info("Submit message for sales order {} published", docNo);
        } catch (AmqpException e) {
            // The payment is already committed and must not be undone for this; the
            // stock stays counted against the order until someone resends it.
            log.error("Unable to publish the submit message for sales order {}", docNo, e);
        }
    }
}
