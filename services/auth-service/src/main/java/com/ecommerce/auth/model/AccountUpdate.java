package com.ecommerce.auth.model;

/** The profile fields an update may change. */
public record AccountUpdate(
        String email,
        String firstName,
        String lastName) {

    /**
     * True when every field is {@code null} — there is nothing for the identity
     * provider to do, so the service can reject the call instead of spending a
     * round trip on it.
     */
    public boolean isEmpty() {
        return email == null && firstName == null && lastName == null;
    }
}
