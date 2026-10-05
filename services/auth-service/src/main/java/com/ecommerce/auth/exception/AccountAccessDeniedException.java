package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/**
 * The caller was authenticated, but the token carries no {@code sub} claim, so
 * there is no account for the request to act on.
 */
public class AccountAccessDeniedException extends AccountException {

    public AccountAccessDeniedException(String message) {
        super(AccountErrorCode.ACCOUNT_ACCESS_DENIED, message);
    }
}
