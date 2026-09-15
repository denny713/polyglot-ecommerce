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
 * <p>
 * The names are properties rather than literals because the producer — the order
 * service — owns them just as much as this service does; both sides must be able to
 * point at the same exchange without a rebuild.
 * <p>
 * Everything declared here is durable. A submit message represents stock that has
 * already been sold, so it must survive a broker restart rather than be dropped
 * with the connection.
 * <p>
 * The work queue carries {@code x-dead-letter-exchange}, which is where the
 * failure handling actually lives: {@code spring.rabbitmq.listener.simple.default-requeue-rejected}
 * is false, so a message the listener throws on is rejected once and routed to the
 * dead letter queue instead of being handed back to the same consumer forever. A
 * message that failed for a transient reason is therefore replayed from the dead
 * letter queue, not lost — see {@code SalesOrderConsumer} for which failures are
 * expected to end up there.
 * <p>
 * Declaring the exchange and its bindings here, on the consuming side, is
 * deliberate: the queue only exists because this service listens on it, so nothing
 * is created that has no consumer.
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
     * <p>
     * A single {@code MessageConverter} bean is what both the listener container
     * factory and {@code RabbitTemplate} auto-configuration pick up, so declaring
     * it here is enough for the whole service.
     * <p>
     * The type precedence is the part that matters for interoperability. Left to
     * a {@code __TypeId__} header, the body would only be readable when the
     * producer is a JVM service whose class name this service also trusts, and a
     * class rename on the other side would break the queue. Reading the body into
     * the type the listener method declares instead means the contract is the JSON
     * shape, not a class name.
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);

        return converter;
    }
}
