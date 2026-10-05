package com.ecommerce.auth.enums;

/**
 * What happened to an account, as the notification service reads it off the
 * wire. The names are the contract — the consumer picks its email template by
 * them — so renaming a constant is a breaking change on the other side.
 */
public enum AccountEventType {
    ACCOUNT_REGISTERED,
    ACCOUNT_UPDATED,
    PASSWORD_CHANGED,
    ACCOUNT_DELETED
}
