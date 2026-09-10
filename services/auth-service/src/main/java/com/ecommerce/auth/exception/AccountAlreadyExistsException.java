package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/**
 * The username or the email address is already registered in the realm.
 *
 * <p>
 * Keycloak reports both cases as HTTP 409, and this service does not try to tell
 * them apart in the message: saying <em>which</em> of the two collided turns the
 * registration endpoint into a user enumeration oracle, which is the same reason
 * {@code InvalidCredentialsException} never says whether it was the username or
 * the password that was wrong.
 */
public class AccountAlreadyExistsException extends AccountException {

    public AccountAlreadyExistsException(String message) {
        super(AccountErrorCode.ACCOUNT_ALREADY_EXISTS, message);
    }
}
