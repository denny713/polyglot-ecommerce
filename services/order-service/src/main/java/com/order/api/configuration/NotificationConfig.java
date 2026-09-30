package com.order.api.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The producing side of the order notifications the notification service mails out. */
@Slf4j
@Configuration
public class NotificationConfig {

    private final String exchange;

    public NotificationConfig(@Value("${rabbitmq.exchange.notification}") String exchange) {
        this.exchange = exchange;
    }

    /**
     * The exchange auth-service already publishes account events to. It is declared
     * here with the same type and flags, since a broker refuses a redeclaration that
     * differs from what exists.
     */
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(exchange, true, false);
    }

    /** The template notifications are published with, once the change behind them commits. */
    @Bean
    public RabbitTemplate notificationRabbitTemplate(ConnectionFactory connectionFactory,
                                                     MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setExchange(exchange);
        template.setMandatory(true);

        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("Broker did not confirm notification message [{}]: {}",
                        correlation == null ? "no correlation" : correlation.getId(), cause);
            }
        });

        template.setReturnsCallback(returned -> log.error(
                "Notification message returned unrouted from exchange [{}] with routing key [{}]: {} {}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()));

        return template;
    }
}
