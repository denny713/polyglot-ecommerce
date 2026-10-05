package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AuthErrorCode;

/**
 * The refresh token presented was rejected by the identity provider: it has
 * expired, was already used to log out, or belongs to a session that no longer
 * exists.
 */
public class InvalidRefreshTokenException extends AuthenticationException {

    public InvalidRefreshTokenException(String message) {
        super(AuthErrorCode.INVALID_REFRESH_TOKEN, message);
    }
}