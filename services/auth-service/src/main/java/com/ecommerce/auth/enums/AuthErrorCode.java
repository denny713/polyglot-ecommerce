package com.ecommerce.auth.enums;

/**
 * Domain error codes for authentication failures.
 *
 * <p>
 * The domain layer deliberately knows nothing about HTTP statuses. Mapping these
 * codes to status codes happens in
 * {@code com.ecommerce.auth.exception.handler.AuthenticationExceptionMapper}, so
 * adding a new kind of failure only takes one constant here plus one line in the
 * mapper — without touching the controller or the service.
 */
public enum AuthErrorCode {

    /**
     * Wrong username or password.
     */
    INVALID_CREDENTIALS,

    /**
     * Account temporarily locked by Keycloak's brute force detection.
     */
    ACCOUNT_LOCKED,

    /**
     * The account exists but is disabled, or has pending required actions.
     */
    ACCOUNT_DISABLED
}
