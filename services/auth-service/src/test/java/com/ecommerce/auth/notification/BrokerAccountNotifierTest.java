package com.ecommerce.auth.notification;

import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.RawPassword;
import com.ecommerce.auth.notification.broker.BrokerAccountNotifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** Unit tests for the account events handed to the notification service. */
class BrokerAccountNotifierTest {

    private static final Account ACCOUNT = new Account(
            "8f1a5c2e", "denny.afrizal", "denny@mail.com", "Denny", "Afrizal", true);

    private static final RawPassword PASSWORD = new RawPassword("K7mQ2x#9");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Emitter<String> emitter;
    private BrokerAccountNotifier notifier;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        emitter = mock(Emitter.class);
        notifier = new BrokerAccountNotifier(emitter, objectMapper, Duration.ofMillis(200));
    }

    // ------------------------------------------------------------------
    // registration
    // ------------------------------------------------------------------

    @Test
    void shouldPublishTheGeneratedPasswordToTheAccountAddress() throws Exception {
        confirmEverything();

        notifier.sendTemporaryPassword(ACCOUNT, PASSWORD);

        JsonNode event = body(captured());
        assertEquals("ACCOUNT_REGISTERED", event.get("eventType").asText());
        assertEquals("auth-service", event.get("source").asText());
        assertEquals("8f1a5c2e", event.get("accountId").asText());
        assertEquals("denny.afrizal", event.get("username").asText());
        assertEquals("denny@mail.com", event.get("email").asText());
        assertEquals("Denny", event.get("firstName").asText());
        assertEquals("K7mQ2x#9", event.at("/data/temporaryPassword").asText());
        assertFalse(event.get("eventId").asText().isBlank());
        assertFalse(event.get("occurredAt").asText().isBlank());
    }

    @Test
    void shouldPublishPersistentJson() {
        confirmEverything();

        notifier.sendTemporaryPassword(ACCOUNT, PASSWORD);

        OutgoingRabbitMQMetadata metadata = captured().getMetadata(OutgoingRabbitMQMetadata.class).orElseThrow();
        assertEquals("application/json", metadata.getContentType());
        assertEquals(2, metadata.getDeliveryMode(), "a lost message is a lost password");
        assertEquals("ACCOUNT_REGISTERED", metadata.getType());
    }

    /** The caller undoes the registration on this, so a refusal must reach it. */
    @Test
    void shouldReportABrokerRefusal() {
        IllegalStateException refused = new IllegalStateException("nack");
        doAnswer(answer(message -> message.nack(refused))).when(emitter).send(any(Message.class));

        NotificationDeliveryException thrown = assertThrows(NotificationDeliveryException.class,
                () -> notifier.sendTemporaryPassword(ACCOUNT, PASSWORD));

        assertSame(refused, thrown.getCause(), "the real fault must stay in the log");
    }

    @Test
    void shouldGiveUpWhenTheBrokerNeverConfirms() {
        NotificationDeliveryException thrown = assertThrows(NotificationDeliveryException.class,
                () -> notifier.sendTemporaryPassword(ACCOUNT, PASSWORD));

        assertInstanceOf(TimeoutException.class, thrown.getCause());
    }

    @Test
    void shouldReportAChannelThatRefusesTheMessageOutright() {
        IllegalStateException full = new IllegalStateException("buffer full");
        doThrow(full).when(emitter).send(any(Message.class));

        assertSame(full, assertThrows(NotificationDeliveryException.class,
                () -> notifier.sendTemporaryPassword(ACCOUNT, PASSWORD)).getCause());
    }

    /** A message that cannot be written never reaches the channel at all. */
    @Test
    void shouldReportAnEventThatCannotBeSerialized() throws Exception {
        ObjectMapper broken = mock(ObjectMapper.class);
        JsonProcessingException unwritable = new JsonProcessingException("unwritable") { };
        doThrow(unwritable).when(broken).writeValueAsString(any());
        notifier = new BrokerAccountNotifier(emitter, broken, Duration.ofMillis(200));

        assertSame(unwritable, assertThrows(NotificationDeliveryException.class,
                () -> notifier.sendTemporaryPassword(ACCOUNT, PASSWORD)).getCause());
        verify(emitter, never()).send(any(Message.class));
    }

    @Test
    void shouldRestoreTheInterruptFlagWhileWaiting() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(NotificationDeliveryException.class,
                    () -> notifier.sendTemporaryPassword(ACCOUNT, PASSWORD));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    // ------------------------------------------------------------------
    // after-the-fact notifications
    // ------------------------------------------------------------------

    @Test
    void shouldNameOnlyTheFieldsTheUpdateChanged() throws Exception {
        notifier.notifyAccountUpdated(ACCOUNT, new AccountUpdate("denny@mail.com", null, "Afrizal"));

        JsonNode event = body(captured());
        assertEquals("ACCOUNT_UPDATED", event.get("eventType").asText());
        assertEquals(List.of("email", "lastName"),
                objectMapper.convertValue(event.at("/data/changedFields"), List.class));
    }

    @Test
    void shouldNameTheFirstNameWhenItChanged() throws Exception {
        notifier.notifyAccountUpdated(ACCOUNT, new AccountUpdate(null, "Den", null));

        assertEquals(List.of("firstName"),
                objectMapper.convertValue(body(captured()).at("/data/changedFields"), List.class));
    }

    @Test
    void shouldPublishAPasswordChangeWithoutAnyPassword() throws Exception {
        notifier.notifyPasswordChanged(ACCOUNT);

        JsonNode event = body(captured());
        assertEquals("PASSWORD_CHANGED", event.get("eventType").asText());
        assertTrue(event.get("data").isEmpty(), "neither the old nor the new password belongs on the wire");
    }

    @Test
    void shouldPublishADelete() throws Exception {
        notifier.notifyAccountDeleted(ACCOUNT);

        assertEquals("ACCOUNT_DELETED", body(captured()).get("eventType").asText());
    }

    /** Nothing waits on these, so a late answer from the broker is only logged. */
    @Test
    void shouldNotWaitForTheBrokerAfterTheFact() {
        doAnswer(answer(message -> message.nack(new IllegalStateException("nack"))))
                .when(emitter).send(any(Message.class));
        notifier.notifyPasswordChanged(ACCOUNT);

        confirmEverything();
        notifier.notifyAccountDeleted(ACCOUNT);
    }

    @Test
    void shouldReportAChannelThatRefusesAnAfterTheFactMessage() {
        doThrow(new IllegalStateException("buffer full")).when(emitter).send(any(Message.class));

        assertThrows(NotificationDeliveryException.class, () -> notifier.notifyAccountDeleted(ACCOUNT));
    }

    // ------------------------------------------------------------------

    private void confirmEverything() {
        doAnswer(answer(Message::ack)).when(emitter).send(any(Message.class));
    }

    @SuppressWarnings("unchecked")
    private static Answer<Void> answer(java.util.function.Consumer<Message<String>> reaction) {
        return invocation -> {
            reaction.accept(invocation.getArgument(0, Message.class));
            return null;
        };
    }

    @SuppressWarnings("unchecked")
    private Message<String> captured() {
        ArgumentCaptor<Message<String>> captor = ArgumentCaptor.forClass(Message.class);
        verify(emitter).send(captor.capture());
        return captor.getValue();
    }

    private JsonNode body(Message<String> message) throws Exception {
        return objectMapper.readTree(message.getPayload());
    }
}
