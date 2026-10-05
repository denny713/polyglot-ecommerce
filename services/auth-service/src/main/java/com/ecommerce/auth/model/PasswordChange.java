package com.ecommerce.auth.model;

/**
 * A request to replace an account's password, carrying the current one as proof
 * that the person asking is the person who owns it.
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

    /** True when the "change" would leave the password exactly as it was. */
    public boolean isNoOp() {
        return oldPassword.equals(newPassword);
    }

    @Override
    public String toString() {
        return "PasswordChange[oldPassword=***, newPassword=***]";
    }
}
