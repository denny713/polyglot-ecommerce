package com.ecommerce.auth.exception;

/** Username tidak dikenal, atau password tidak cocok. */
public class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException(String message) {
        super(AuthErrorCode.INVALID_CREDENTIALS, message);
    }
}
