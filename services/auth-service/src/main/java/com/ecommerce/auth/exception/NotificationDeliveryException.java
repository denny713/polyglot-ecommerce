package com.ecommerce.auth.exception;

/** The message carrying a newly generated password could not be handed to the mail server. */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
