package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/** No account with the given id exists in the realm — it was never created, or it has already been deleted. */
public class AccountNotFoundException extends AccountException {

    public AccountNotFoundException(String message) {
        super(AccountErrorCode.ACCOUNT_NOT_FOUND, message);
    }
}
