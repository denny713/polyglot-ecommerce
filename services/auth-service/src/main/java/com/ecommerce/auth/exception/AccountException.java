package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

import java.util.Objects;

/**
 * Parent of all <em>expected</em> account management failures — the ones caused
 * by the caller's input or by what already exists in the realm, rather than by a
 * technical error.
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
