package com.ecommerce.auth.exception;

/**
 * Keycloak could not be reached, timed out, or answered with an error unrelated
 * to credentials (5xx, unreadable response).
 *
 * <p>
 * Deliberately does not extend {@link AuthenticationException}: this is an
 * infrastructure failure (HTTP 503), not a problem with the user's credentials
 * (HTTP 401).
 */
public class IdentityProviderUnavailableException extends RuntimeException {

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
