package com.ecommerce.auth.exception;

/** An account notification could not be handed to the message broker. */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
