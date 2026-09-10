package com.ecommerce.auth.exception;

import com.ecommerce.auth.enums.AccountErrorCode;

/**
 * The caller was authenticated, but the token carries no {@code sub} claim, so
 * there is no account for the request to act on.
 *
 * <p>
 * Close to unreachable, and deliberately kept anyway. The account endpoints
 * take their subject from the token rather than from the URL, which is what
 * makes it impossible to address somebody else's account; this is the one thing
 * that arrangement can still fail on, and answering it explicitly beats letting
 * a {@code null} id travel down to the identity provider and come back as a
 * confusing 500.
 */
public class AccountAccessDeniedException extends AccountException {

    public AccountAccessDeniedException(String message) {
        super(AccountErrorCode.ACCOUNT_ACCESS_DENIED, message);
    }
}
