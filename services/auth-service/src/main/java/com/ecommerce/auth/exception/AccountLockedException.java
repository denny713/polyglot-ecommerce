package com.ecommerce.auth.exception;

/**
 * Akun terkunci sementara karena terlalu banyak percobaan login gagal
 * (brute force detection realm {@code ecommerce}).
 */
public class AccountLockedException extends AuthenticationException {

    public AccountLockedException(String message) {
        super(AuthErrorCode.ACCOUNT_LOCKED, message);
    }
}
