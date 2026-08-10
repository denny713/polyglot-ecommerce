package com.ecommerce.auth.exception;

/**
 * Akun dinonaktifkan, atau profilnya belum lengkap sehingga Keycloak menolak
 * login dengan "Account is not fully set up".
 */
public class AccountDisabledException extends AuthenticationException {

    public AccountDisabledException(String message) {
        super(AuthErrorCode.ACCOUNT_DISABLED, message);
    }
}
