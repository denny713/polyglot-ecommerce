package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/** The identity provider rejected the account data even though it passed Bean Validation on the way in. */
public class InvalidAccountDataException extends AccountException {

    public InvalidAccountDataException(String message) {
        super(AccountErrorCode.INVALID_ACCOUNT_DATA, message);
    }
}
