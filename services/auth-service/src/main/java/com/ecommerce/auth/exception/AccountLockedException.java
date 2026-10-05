package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AuthErrorCode;

/**
 * The account is temporarily locked because of too many failed login attempts
 * (brute force detection on the {@code ecommerce} realm).
 */
public class AccountLockedException extends AuthenticationException {

    public AccountLockedException(String message) {
        super(AuthErrorCode.ACCOUNT_LOCKED, message);
    }
}
