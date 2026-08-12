package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AuthErrorCode;

import java.util.Objects;

/**
 * Parent of all <em>expected</em> authentication failures — the ones caused by
 * user input rather than by a technical error.
 *
 * <p>
 * Every subclass can be handled uniformly by a single exception mapper (Liskov
 * Substitution Principle): the mapper only needs to read {@link #errorCode()}, it
 * never needs to know the concrete class.
 */
public abstract class AuthenticationException extends RuntimeException {

    private final AuthErrorCode errorCode;

    protected AuthenticationException(AuthErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    protected AuthenticationException(AuthErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public AuthErrorCode errorCode() {
        return errorCode;
    }
}
