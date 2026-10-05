package com.ecommerce.auth.exception;

/**
 * Keycloak could not be reached, timed out, or answered with an error unrelated
 * to credentials (5xx, unreadable response).
 */
public class IdentityProviderUnavailableException extends RuntimeException {

    public IdentityProviderUnavailableException(String message) {
        super(message);
    }

    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
