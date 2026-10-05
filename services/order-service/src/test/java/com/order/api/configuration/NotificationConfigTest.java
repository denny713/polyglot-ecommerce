package com.order.api.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.expression.Expression;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** Tests what this service publishes notifications with. */
class NotificationConfigTest {

    private static final String EXCHANGE = "notification.exchange";

    private final NotificationConfig config = new NotificationConfig(EXCHANGE);
    private final MessageConverter converter = new BrokerConfig("sales.exchange", "sales.order.submitted")
            .jsonMessageConverter();

    private RabbitTemplate template() {
        return config.notificationRabbitTemplate(mock(ConnectionFactory.class), converter);
    }

    @Test
    void shouldDeclareTheExchangeExactlyAsAuthServiceDoes() {
        DirectExchange exchange = config.notificationExchange();

        assertEquals(EXCHANGE, exchange.getName());
        assertEquals("direct", exchange.getType());
        assertTrue(exchange.isDurable());
        assertFalse(exchange.isAutoDelete());
    }

    @Test
    void shouldPublishToTheNotificationExchangeAsJson() {
        RabbitTemplate template = template();

        assertEquals(EXCHANGE, ReflectionTestUtils.getField(template, "exchange"));
        assertSame(converter, template.getMessageConverter());
    }

    @Test
    void shouldAskTheBrokerToReturnAnUnroutableMessage() {
        Expression mandatory = (Expression) ReflectionTestUtils.getField(template(), "mandatoryExpression");
        assertNotNull(mandatory);
        assertTrue(mandatory.getValue(Boolean.class));
    }

    @Test
    void shouldSurviveEveryShapeOfConfirmation() {
        RabbitTemplate.ConfirmCallback confirm = (RabbitTemplate.ConfirmCallback)
                ReflectionTestUtils.getField(template(), "confirmCallback");
        assertNotNull(confirm);

        assertDoesNotThrow(() -> confirm.confirm(new CorrelationData("evt-1"), false, "no queue"));
        assertDoesNotThrow(() -> confirm.confirm(null, false, "no queue"));
        assertDoesNotThrow(() -> confirm.confirm(new CorrelationData("evt-1"), true, null));
    }

    @Test
    void shouldSurviveAReturnedMessage() {
        RabbitTemplate.ReturnsCallback returns = (RabbitTemplate.ReturnsCallback)
                ReflectionTestUtils.getField(template(), "returnsCallback");
        assertNotNull(returns);

        Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        assertDoesNotThrow(() -> returns.returnedMessage(
                new ReturnedMessage(message, 312, "NO_ROUTE", EXCHANGE, "notification.order")));
    }
}
