package com.ecommerce.auth.model;

/**
 * A request to replace an account's password, carrying the current one as proof
 * that the person asking is the person who owns it.
 *
 * <p>
 * The old password is demanded even though the caller already holds a valid
 * access token. A token can be stolen — from browser storage, from a log, from
 * a proxy — and a stolen token that can also change the password is a stolen
 * account, permanently. Asking for the old password is what keeps a leaked
 * token a temporary problem.
 *
 * <p>
 * {@link #toString()} masks both values: the old password is a credential, and
 * the new one is about to become one.
 */
public record PasswordChange(String oldPassword, String newPassword) {

    public PasswordChange {
        requireValue(oldPassword, "oldPassword");
        requireValue(newPassword, "newPassword");
    }

    private static void requireValue(String value, String field) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
    }

    /**
     * True when the "change" would leave the password exactly as it was.
     */
    public boolean isNoOp() {
        return oldPassword.equals(newPassword);
    }

    @Override
    public String toString() {
        return "PasswordChange[oldPassword=***, newPassword=***]";
    }
}
