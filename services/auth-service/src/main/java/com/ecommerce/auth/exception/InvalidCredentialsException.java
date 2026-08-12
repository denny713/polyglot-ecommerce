package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AuthErrorCode;

/** The username is unknown, or the password does not match. */
public class InvalidCredentialsException extends AuthenticationException {

    public InvalidCredentialsException(String message) {
        super(AuthErrorCode.INVALID_CREDENTIALS, message);
    }
}
