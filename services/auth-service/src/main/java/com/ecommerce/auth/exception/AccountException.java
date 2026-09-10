package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

import java.util.Objects;

/**
 * Parent of all <em>expected</em> account management failures — the ones caused
 * by the caller's input or by what already exists in the realm, rather than by a
 * technical error.
 *
 * <p>
 * The account counterpart of {@link AuthenticationException}, and it works the
 * same way: one mapper handles every subclass by reading {@link #errorCode()}
 * alone, so a new kind of failure never reaches the controller (Liskov
 * Substitution Principle).
 *
 * <p>
 * It is a separate hierarchy because the two are mapped to different statuses
 * from different codes, and because a class that only registers accounts should
 * not be catching exceptions named after logins (Interface Segregation
 * Principle, applied to exceptions).
 */
public abstract class AccountException extends RuntimeException {

    private final AccountErrorCode errorCode;

    protected AccountException(AccountErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public AccountErrorCode errorCode() {
        return errorCode;
    }
}
