package com.ecommerce.auth.enums;

/** Domain error codes for authentication failures. */
public enum AuthErrorCode {

    /** Wrong username or password. */
    INVALID_CREDENTIALS,

    /** Account temporarily locked by Keycloak's brute force detection. */
    ACCOUNT_LOCKED,

    /** The account exists but is disabled, or has pending required actions. */
    ACCOUNT_DISABLED,

    /** The refresh token is expired, unknown, or already revoked. */
    INVALID_REFRESH_TOKEN
}
