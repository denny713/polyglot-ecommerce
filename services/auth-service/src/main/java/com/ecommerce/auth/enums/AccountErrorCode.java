package com.ecommerce.auth.enums;

/**
 * Domain error codes for account management failures.
 *
 * <p>
 * Deliberately a separate enum from {@link AuthErrorCode} rather than a handful
 * of extra constants on it: "the username is taken" is not an authentication
 * outcome, and keeping the two apart means
 * {@code AuthenticationExceptionMapper}'s exhaustive {@code switch} does not
 * grow cases it can never reach.
 *
 * <p>
 * As with {@link AuthErrorCode}, the domain layer knows nothing about HTTP
 * statuses — the mapping lives in
 * {@code com.ecommerce.auth.handler.AccountExceptionMapper}.
 */
public enum AccountErrorCode {

    /**
     * The username or email is already registered in the realm.
     */
    ACCOUNT_ALREADY_EXISTS,

    /**
     * No account with the given id exists in the realm.
     */
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
