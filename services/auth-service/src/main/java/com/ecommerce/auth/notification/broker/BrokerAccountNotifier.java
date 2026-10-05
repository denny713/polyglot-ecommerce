package com.ecommerce.auth.notification.broker;

import com.ecommerce.auth.enums.AccountEventType;
import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.RawPassword;
import com.ecommerce.auth.notification.AccountNotifier;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Hands account events to the notification service over RabbitMQ; that service
 * owns the templates and the SMTP connection.
 */
@ApplicationScoped
public class BrokerAccountNotifier implements AccountNotifier {

    private static final Logger LOG = Logger.getLogger(BrokerAccountNotifier.class);

    /**
     * The outgoing channel, configured under
     * {@code mp.messaging.outgoing.account-notification}.
     */
    public static final String CHANNEL = "account-notification";

    static final String SOURCE = "auth-service";

    /**
     * AMQP delivery mode 2: the broker writes the message to disk before confirming
     * it.
     */
    private static final int PERSISTENT = 2;

    private final Emitter<String> emitter;
    private final ObjectMapper objectMapper;
    private final Duration confirmTimeout;

    @Inject
    public BrokerAccountNotifier(@Channel(CHANNEL) Emitter<String> emitter,
            ObjectMapper objectMapper,
            @ConfigProperty(name = "notification.confirm-timeout", defaultValue = "10s") Duration confirmTimeout) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.confirmTimeout = confirmTimeout;
    }

    /**
     * Waits for the broker's confirm: the caller holds the only other copy of
     * the password and undoes the registration if this throws, so "probably
     * sent" is not good enough.
     */
    @Override
    public void sendTemporaryPassword(Account account, RawPassword password) {
        AccountEvent event = event(AccountEventType.ACCOUNT_REGISTERED, account,
                Map.of("temporaryPassword", password.value()));

        try {
            publish(event).get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw undelivered(event, e);
        } catch (ExecutionException e) {
            throw undelivered(event, e.getCause());
        } catch (TimeoutException e) {
            throw undelivered(event, e);
        }

        // The password itself is never logged — that is the whole point of
        // RawPassword#toString. Only the fact of the hand-over is recorded.
        LOG.infof("Handed the generated password for account %s to the notification service", account.id());
    }

    @Override
    public void notifyAccountUpdated(Account account, AccountUpdate update) {
        publishAndForget(event(AccountEventType.ACCOUNT_UPDATED, account,
                Map.of("changedFields", changedFields(update))));
    }

    @Override
    public void notifyPasswordChanged(Account account) {
        publishAndForget(event(AccountEventType.PASSWORD_CHANGED, account, Map.of()));
    }

    @Override
    public void notifyAccountDeleted(Account account) {
        publishAndForget(event(AccountEventType.ACCOUNT_DELETED, account, Map.of()));
    }

    /**
     * For events announcing a change that has already happened: nothing is
     * waiting on the outcome, so a late refusal is only logged.
     */
    private void publishAndForget(AccountEvent event) {
        publish(event).whenComplete((ignored, failure) -> {
            if (failure == null) {
                LOG.debugf("%s event %s for account %s confirmed", event.eventType(), event.eventId(),
                        event.accountId());
            } else {
                LOG.errorf(failure, "%s event %s for account %s was not accepted by the broker",
                        event.eventType(), event.eventId(), event.accountId());
            }
        });
    }

    /**
     * Completes when the broker acknowledges the message, or fails when it refuses
     * it.
     */
    private CompletableFuture<Void> publish(AccountEvent event) {
        String body = serialize(event);
        CompletableFuture<Void> confirmed = new CompletableFuture<>();

        OutgoingRabbitMQMetadata metadata = OutgoingRabbitMQMetadata.builder()
                .withContentType("application/json")
                .withDeliveryMode(PERSISTENT)
                .withMessageId(event.eventId())
                .withType(event.eventType())
                .build();

        Message<String> message = Message.of(body, Metadata.of(metadata),
                () -> {
                    confirmed.complete(null);
                    return CompletableFuture.completedFuture(null);
                },
                failure -> {
                    confirmed.completeExceptionally(failure);
                    return CompletableFuture.completedFuture(null);
                });

        try {
            emitter.send(message);
        } catch (RuntimeException e) {
            // Thrown synchronously when the channel is not wired up yet or its
            // buffer is full — the message never left this process.
            throw undelivered(event, e);
        }
        return confirmed;
    }

    private String serialize(AccountEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw undelivered(event, e);
        }
    }

    private static AccountEvent event(AccountEventType type, Account account, Map<String, Object> data) {
        return new AccountEvent(
                UUID.randomUUID().toString(),
                type.name(),
                Instant.now().toString(),
                SOURCE,
                account.id(),
                account.username(),
                account.email(),
                account.firstName(),
                account.lastName(),
                data);
    }

    private static List<String> changedFields(AccountUpdate update) {
        List<String> fields = new ArrayList<>(3);
        if (update.email() != null) {
            fields.add("email");
        }

        if (update.firstName() != null) {
            fields.add("firstName");
        }

        if (update.lastName() != null) {
            fields.add("lastName");
        }

        return fields;
    }

    private static NotificationDeliveryException undelivered(AccountEvent event, Throwable cause) {
        LOG.errorf(cause, "Could not hand the %s event for account %s to the broker",
                event.eventType(), event.accountId());

        return new NotificationDeliveryException(
                "Could not hand the " + event.eventType() + " notification to the broker", cause);
    }
}
