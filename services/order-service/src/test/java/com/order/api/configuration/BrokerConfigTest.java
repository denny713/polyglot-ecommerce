package com.order.api.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.expression.Expression;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Tests what this service publishes sales orders with. */
class BrokerConfigTest {

    private static final String EXCHANGE = "sales.exchange";
    private static final String ROUTING_KEY = "sales.order.submitted";

    private final BrokerConfig config = new BrokerConfig(EXCHANGE, ROUTING_KEY);

    private RabbitTemplate template() {
        return config.salesRabbitTemplate(
                mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class),
                config.jsonMessageConverter());
    }

    // ------------------------------------------------------------------
    // the topology this side declares
    // ------------------------------------------------------------------

    @Test
    void shouldDeclareADurableTopicExchangeForSalesEvents() {
        TopicExchange exchange = config.salesExchange();

        assertEquals(EXCHANGE, exchange.getName());
        // A sale that is already paid for must outlive a broker restart.
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }

    @Test
    void shouldAgreeWithTheConsumerOnTheUserHeader() {
        // The listener thread has no token filter, so this header is the only way
        // the consumer learns whose order it is auditing.
        assertEquals("X-User-Id", BrokerConfig.USER_HEADER);
    }

    // ------------------------------------------------------------------
    // the payload on the wire
    // ------------------------------------------------------------------

    @Test
    void shouldSendJsonRatherThanJavaSerialization() {
        MessageConverter converter = config.jsonMessageConverter();

        assertInstanceOf(JacksonJsonMessageConverter.class, converter);
    }

    @Test
    void shouldLetTheConsumerDecideTheTargetType() {
        JacksonJsonMessageConverter converter = (JacksonJsonMessageConverter) config.jsonMessageConverter();

        // INFERRED, so the consumer's own method signature wins and the payload does
        // not have to carry a class name only this service knows.
        assertEquals(JacksonJavaTypeMapper.TypePrecedence.INFERRED,
                converter.getJavaTypeMapper().getTypePrecedence());
    }

    // ------------------------------------------------------------------
    // the template the order flow publishes with
    // ------------------------------------------------------------------

    @Test
    void shouldPublishToTheSalesExchangeWithTheSubmitRoutingKey() {
        RabbitTemplate template = template();

        assertEquals(EXCHANGE, ReflectionTestUtils.getField(template, "exchange"));
        assertEquals(ROUTING_KEY, ReflectionTestUtils.getField(template, "routingKey"));
    }

    @Test
    void shouldUseTheJsonConverter() {
        MessageConverter converter = config.jsonMessageConverter();

        RabbitTemplate template = config.salesRabbitTemplate(
                mock(org.springframework.amqp.rabbit.connection.ConnectionFactory.class), converter);

        assertSame(converter, template.getMessageConverter());
    }

    @Test
    void shouldAskTheBrokerToReturnAnUnroutableMessage() {
        // Without mandatory, a message with no queue bound to it is dropped in
        // silence and the sale is never seen by inventory. The template keeps the
        // flag as the expression it evaluates per message.
        Expression mandatory = (Expression) ReflectionTestUtils.getField(template(), "mandatoryExpression");
        assertNotNull(mandatory);
        assertTrue(mandatory.getValue(Boolean.class));
    }

    @Test
    void shouldRegisterBothPublisherCallbacks() {
        RabbitTemplate template = template();

        assertNotNull(ReflectionTestUtils.getField(template, "confirmCallback"));
        assertNotNull(ReflectionTestUtils.getField(template, "returnsCallback"));
    }

    @Test
    void shouldSurviveEveryShapeOfConfirmation() {
        RabbitTemplate.ConfirmCallback confirm = (RabbitTemplate.ConfirmCallback)
                ReflectionTestUtils.getField(template(), "confirmCallback");
        assertNotNull(confirm);

        // A nack with a correlation, a nack without one (which is what a broker
        // sends when it cannot tie the confirm to a publish), and an ack.
        assertDoesNotThrow(() -> confirm.confirm(new CorrelationData("so-1"), false, "no queue"));
        assertDoesNotThrow(() -> confirm.confirm(null, false, "no queue"));
        assertDoesNotThrow(() -> confirm.confirm(new CorrelationData("so-1"), true, null));
    }

    @Test
    void shouldSurviveAReturnedMessage() {
        RabbitTemplate.ReturnsCallback returns = (RabbitTemplate.ReturnsCallback)
                ReflectionTestUtils.getField(template(), "returnsCallback");
        assertNotNull(returns);

        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertDoesNotThrow(() -> returns.returnedMessage(
                new ReturnedMessage(message, 312, "NO_ROUTE", EXCHANGE, ROUTING_KEY)));
    }
}
