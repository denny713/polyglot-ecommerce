package com.ecommerce.auth.notification.broker;

import java.util.Map;

/**
 * The JSON message the notification service consumes. Flat on purpose: the
 * consumer is not written in Java and should not have to know how this service
 * models an account to find the address it is mailing.
 */
public record AccountEvent(
        String eventId,
        String eventType,
        String occurredAt,
        String source,
        String accountId,
        String username,
        String email,
        String firstName,
        String lastName,
        Map<String, Object> data) {
}
