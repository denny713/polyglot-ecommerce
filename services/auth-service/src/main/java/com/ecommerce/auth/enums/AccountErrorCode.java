package com.ecommerce.auth.enums;

/** Domain error codes for account management failures. */
public enum AccountErrorCode {

    /** The username or email is already registered in the realm. */
    ACCOUNT_ALREADY_EXISTS,

    /** No account with the given id exists in the realm. */
    ACCOUNT_NOT_FOUND,

    /**
     * The identity provider rejected the account data — most often a password
     * that does not satisfy the realm's password policy.
     */
    INVALID_ACCOUNT_DATA,

    /**
     * The caller is authenticated, but the token carries no subject, so there is
     * no account for the request to act on.
     */
    ACCOUNT_ACCESS_DENIED
}
