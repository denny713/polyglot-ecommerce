package com.ecommerce.auth.exception;

/**
 * The message carrying a newly generated password could not be handed to the
 * mail server.
 *
 * <p>
 * Deliberately not an {@link AccountException}: nothing about the caller's
 * request was wrong, so this is an infrastructure failure (HTTP 503) in the same
 * family as {@link IdentityProviderUnavailableException}. It is a separate type
 * from that one because the two point an operator at different machines.
 *
 * <p>
 * Registration treats this as fatal and removes the account it just created —
 * see {@code AccountServiceImpl#register}. An account whose only password went
 * nowhere cannot be logged in to, and it would sit there holding the username
 * and email address against the retry.
 */
public class NotificationDeliveryException extends RuntimeException {

    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
