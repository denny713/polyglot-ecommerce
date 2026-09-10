package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/**
 * The identity provider rejected the account data even though it passed Bean
 * Validation on the way in.
 *
 * <p>
 * In practice this is the realm's password policy — length, character classes,
 * password history — which lives in Keycloak and deliberately not in this
 * service: duplicating it here would give two places to change it, and they
 * would drift.
 */
public class InvalidAccountDataException extends AccountException {

    public InvalidAccountDataException(String message) {
        super(AccountErrorCode.INVALID_ACCOUNT_DATA, message);
    }
}
