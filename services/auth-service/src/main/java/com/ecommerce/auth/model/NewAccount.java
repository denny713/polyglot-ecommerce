package com.ecommerce.auth.model;

/**
 * The profile of an account to be created, in a form that is neutral with
 * respect to the identity provider.
 *
 * <p>
 * There is no password here. The caller does not choose one: registration
 * generates a {@link RawPassword} and mails it to {@link #email()}, which
 * is what makes the address load-bearing rather than a contact detail — an
 * account registered against an address nobody reads can never be logged in to.
 *
 * <p>
 * Email and both names are mandatory for a second reason as well: Keycloak 26
 * enables the "Verify Profile" required action by default, and an account
 * missing any of them is created successfully but then rejected at login with
 * "Account is not fully set up" — a failure that would surface far away from the
 * call that caused it.
 */
public record NewAccount(
        String username,
        String email,
        String firstName,
        String lastName) {

    public NewAccount {
        requireText(username, "username");
        requireText(email, "email");
        requireText(firstName, "firstName");
        requireText(lastName, "lastName");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
