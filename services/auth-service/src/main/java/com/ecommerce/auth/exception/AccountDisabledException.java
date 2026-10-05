package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AuthErrorCode;

/**
 * The account is disabled, or its profile is incomplete so that Keycloak rejects
 * the login with "Account is not fully set up".
 */
public class AccountDisabledException extends AuthenticationException {

    public AccountDisabledException(String message) {
        super(AuthErrorCode.ACCOUNT_DISABLED, message);
    }
}
