package com.inventory.api.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the broker topology this service consumes from, and the converter that
 * turns a message body into a request object.
 */
@Configuration
public class BrokerConfig {

    private final String exchange;
    private final String deadLetterExchange;
    private final String submitQueue;
    private final String submitDeadLetterQueue;
    private final String submitRoutingKey;
    private final String submitDeadLetterRoutingKey;

    public BrokerConfig(
            @Value("${rabbitmq.exchange.sales}") String exchange,
            @Value("${rabbitmq.exchange.sales-dlx}") String deadLetterExchange,
            @Value("${rabbitmq.queue.so-submit}") String submitQueue,
            @Value("${rabbitmq.queue.so-submit-dlq}") String submitDeadLetterQueue,
            @Value("${rabbitmq.routing-key.so-submit}") String submitRoutingKey,
            @Value("${rabbitmq.routing-key.so-submit-dlq}") String submitDeadLetterRoutingKey) {
        this.exchange = exchange;
        this.deadLetterExchange = deadLetterExchange;
        this.submitQueue = submitQueue;
        this.submitDeadLetterQueue = submitDeadLetterQueue;
        this.submitRoutingKey = submitRoutingKey;
        this.submitDeadLetterRoutingKey = submitDeadLetterRoutingKey;
    }

    @Bean
    public TopicExchange salesExchange() {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    public TopicExchange salesDeadLetterExchange() {
        return new TopicExchange(deadLetterExchange, true, false);
    }

    @Bean
    public Queue salesOrderSubmitQueue() {
        return QueueBuilder.durable(submitQueue)
                .deadLetterExchange(deadLetterExchange)
                .deadLetterRoutingKey(submitDeadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue salesOrderSubmitDeadLetterQueue() {
        return QueueBuilder.durable(submitDeadLetterQueue).build();
    }

    @Bean
    public Binding salesOrderSubmitBinding() {
        return BindingBuilder.bind(salesOrderSubmitQueue()).to(salesExchange()).with(submitRoutingKey);
    }

    @Bean
    public Binding salesOrderSubmitDeadLetterBinding() {
        return BindingBuilder.bind(salesOrderSubmitDeadLetterQueue())
                .to(salesDeadLetterExchange())
                .with(submitDeadLetterRoutingKey);
    }

    /**
     * JSON on the wire rather than the default Java serialization, so the producer
     * does not have to be a JVM service and the payload stays readable in the
     * management UI.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);

        return converter;
    }
}
