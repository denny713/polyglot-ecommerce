package com.inventory.api.configuration;

import com.inventory.api.consumer.SalesOrderConsumer;
import com.inventory.api.model.dto.request.so.SOSubmitReq;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the declared broker topology. */
class BrokerConfigTest {

    private static final String EXCHANGE = "sales.exchange";
    private static final String DLX = "sales.dlx.exchange";
    private static final String QUEUE = "inventory.so.submit";
    private static final String DLQ = "inventory.so.submit.dlq";
    private static final String ROUTING_KEY = "sales.order.submitted";
    private static final String DLQ_ROUTING_KEY = "sales.order.submitted.dlq";

    private final BrokerConfig config = new BrokerConfig(EXCHANGE, DLX, QUEUE, DLQ, ROUTING_KEY, DLQ_ROUTING_KEY);

    @Test
    void shouldDeclareADurableTopicExchangeForSalesEvents() {
        TopicExchange exchange = config.salesExchange();

        assertEquals(EXCHANGE, exchange.getName());
        // A sale that is already paid for must outlive a broker restart.
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }

    @Test
    void shouldDeclareADurableDeadLetterExchange() {
        TopicExchange exchange = config.salesDeadLetterExchange();

        assertEquals(DLX, exchange.getName());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }

    @Test
    void shouldSendFailedMessagesToTheDeadLetterExchange() {
        Queue queue = config.salesOrderSubmitQueue();

        assertEquals(QUEUE, queue.getName());
        assertTrue(queue.isDurable());
        // Without these two the container would drop a rejected message, because
        // default-requeue-rejected is false and nothing else would catch it.
        assertEquals(DLX, queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(DLQ_ROUTING_KEY, queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    void shouldKeepTheDeadLetterQueueAsTheLastStop() {
        Queue queue = config.salesOrderSubmitDeadLetterQueue();

        assertEquals(DLQ, queue.getName());
        assertTrue(queue.isDurable());
        // A dead letter target of its own would move the problem instead of
        // holding it until someone reads it.
        assertNull(queue.getArguments().get("x-dead-letter-exchange"));
    }

    @Test
    void shouldBindTheWorkQueueToTheSubmitRoutingKey() {
        Binding binding = config.salesOrderSubmitBinding();

        assertEquals(QUEUE, binding.getDestination());
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
        assertEquals(EXCHANGE, binding.getExchange());
        assertEquals(ROUTING_KEY, binding.getRoutingKey());
    }

    @Test
    void shouldBindTheDeadLetterQueueToTheKeyTheWorkQueueDeadLettersWith() {
        Binding binding = config.salesOrderSubmitDeadLetterBinding();

        assertEquals(DLQ, binding.getDestination());
        assertEquals(DLX, binding.getExchange());
        // Must match x-dead-letter-routing-key, or rejected messages are dropped
        // by the dead letter exchange for want of a matching binding.
        assertEquals(DLQ_ROUTING_KEY, binding.getRoutingKey());
        assertEquals(config.salesOrderSubmitQueue().getArguments().get("x-dead-letter-routing-key"),
                binding.getRoutingKey());
    }

    // ------------------------------------------------------------------
    // message conversion
    // ------------------------------------------------------------------

    @Test
    void shouldCarryTheBodyAsJson() {
        SOSubmitReq req = new SOSubmitReq();
        req.setId(90L);

        Message message = config.jsonMessageConverter().toMessage(req, new MessageProperties());

        assertEquals("application/json", message.getMessageProperties().getContentType());
        // Readable in the management UI, and parsable by a producer that is not a
        // JVM service.
        assertEquals("{\"id\":90}", new String(message.getBody(), StandardCharsets.UTF_8));
    }

    @Test
    void shouldReadABodyThatCarriesNoTypeHeader() {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        // What the listener container sets from the method signature, and the only
        // thing a message from a non-JVM producer can be read with.
        properties.setInferredArgumentType(SOSubmitReq.class);

        Message message = new Message("{\"id\":90}".getBytes(StandardCharsets.UTF_8), properties);
        Object read = config.jsonMessageConverter().fromMessage(message);

        assertInstanceOf(SOSubmitReq.class, read);
        assertEquals(90L, ((SOSubmitReq) read).getId());
    }

    @Test
    void shouldPreferTheListenerArgumentOverTheSendersClassName() {
        MessageConverter converter = config.jsonMessageConverter();
        SOSubmitReq req = new SOSubmitReq();
        req.setId(90L);

        // A JVM producer stamps its own class into __TypeId__. Following it would
        // mean this service has to know and trust that class name; the inferred
        // type is read instead, so only the JSON shape has to match.
        Message message = converter.toMessage(req, new MessageProperties());
        message.getMessageProperties().setHeader("__TypeId__", "com.order.api.model.dto.request.SOSubmitReq");
        message.getMessageProperties().setInferredArgumentType(SOSubmitReq.class);

        Object read = converter.fromMessage(message);

        assertInstanceOf(SOSubmitReq.class, read);
        assertEquals(90L, ((SOSubmitReq) read).getId());
    }

    @Test
    void shouldBeTheConverterTheAutoConfigurationPicksUp() {
        // Both RabbitTemplate and the listener container factory adopt a single
        // MessageConverter bean; anything else here would silently leave the
        // listener on Java serialization.
        MessageConverter converter = config.jsonMessageConverter();

        assertInstanceOf(JacksonJsonMessageConverter.class, converter);
        assertEquals(JacksonJavaTypeMapper.TypePrecedence.INFERRED,
                ((JacksonJsonMessageConverter) converter).getTypePrecedence());
    }

    // ------------------------------------------------------------------
    // wiring
    // ------------------------------------------------------------------

    @Test
    void shouldListenOnTheQueueThisConfigurationDeclares() throws NoSuchMethodException {
        Method listener = SalesOrderConsumer.class.getMethod("doConsumeSubmit", SOSubmitReq.class, String.class);

        String listenedTo = listener.getAnnotation(RabbitListener.class).queues()[0];

        // Two placeholders pointing at different properties would leave a declared
        // queue with no consumer, and a consumer on a queue nothing binds.
        assertEquals(submitQueueProperty(), listenedTo);
    }

    /** The placeholder the constructor binds the work queue name from. */
    private static String submitQueueProperty() {
        Annotation[][] parameters = BrokerConfig.class.getConstructors()[0].getParameterAnnotations();

        return Arrays.stream(parameters[2])
                .filter(Value.class::isInstance)
                .map(annotation -> ((Value) annotation).value())
                .findFirst()
                .orElseThrow();
    }
}
