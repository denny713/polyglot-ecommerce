package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/** The username or the email address is already registered in the realm. */
public class AccountAlreadyExistsException extends AccountException {

    public AccountAlreadyExistsException(String message) {
        super(AccountErrorCode.ACCOUNT_ALREADY_EXISTS, message);
    }
}
