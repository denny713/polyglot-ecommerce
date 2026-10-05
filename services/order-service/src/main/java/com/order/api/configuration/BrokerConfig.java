package com.order.api.configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The producing side of the sales order flow. */
@Slf4j
@Configuration
public class BrokerConfig {

    /**
     * Carries the id of the user whose order produced the message, and is what the
     * inventory service audits its stock rows with — a listener thread never passes
     * through a token filter, so the only user it can know is the one sent with the
     * message. Must match {@code SalesOrderConsumer.USER_HEADER} on the other side.
     */
    public static final String USER_HEADER = "X-User-Id";

    private final String exchange;
    private final String submitRoutingKey;

    public BrokerConfig(
            @Value("${rabbitmq.exchange.sales}") String exchange,
            @Value("${rabbitmq.routing-key.so-submit}") String submitRoutingKey) {
        this.exchange = exchange;
        this.submitRoutingKey = submitRoutingKey;
    }

    /**
     * Durable and never auto-deleted: a submit message stands for stock that has
     * already been sold and paid for, so it must outlive both a broker restart and
     * the moment this service happens to hold no connection.
     */
    @Bean
    public TopicExchange salesExchange() {
        return new TopicExchange(exchange, true, false);
    }

    /**
     * JSON on the wire rather than Java serialization, so the payload stays readable
     * in the management UI and the consumer does not have to share a class with this
     * service.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);

        return converter;
    }

    /** The template the order flow publishes with, once the order is committed. */
    @Bean
    public RabbitTemplate salesRabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setExchange(exchange);
        template.setRoutingKey(submitRoutingKey);
        template.setMandatory(true);

        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                log.error("Broker did not confirm sales order message [{}]: {}",
                        correlation == null ? "no correlation" : correlation.getId(), cause);
            }
        });

        template.setReturnsCallback(returned -> log.error(
                "Sales order message returned unrouted from exchange [{}] with routing key [{}]: {} {}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()));

        return template;
    }
}
