package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/**
 * No account with the given id exists in the realm — it was never created, or it
 * has already been deleted.
 *
 * <p>
 * Unlike logout, delete is <em>not</em> silently idempotent. Both endpoints
 * require the caller to already hold the id of an account it is allowed to
 * manage, so answering 404 leaks nothing it did not already know, and a client
 * that mistyped an id is better served by being told so.
 */
public class AccountNotFoundException extends AccountException {

    public AccountNotFoundException(String message) {
        super(AccountErrorCode.ACCOUNT_NOT_FOUND, message);
    }
}
