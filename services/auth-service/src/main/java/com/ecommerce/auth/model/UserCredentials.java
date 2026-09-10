package com.ecommerce.auth.model;

/**
 * The credentials used during login.
 *
 * <p>
 * This is a domain model, not a JPA entity: the source of truth for usernames and
 * passwords is Keycloak (the {@code USER_ENTITY} / {@code CREDENTIAL} tables in the
 * {@code keycloak} database), and this service never reads those tables directly —
 * see {@code com.ecommerce.auth.dao.IdentityProviderDao}.
 *
 * <p>
 * {@link #toString()} is deliberately overridden so that the password is never
 * printed to the logs.
 */
public record UserCredentials(String username, String password) {

    public UserCredentials {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }

        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }

    @Override
    public String toString() {
        return "UserCredentials[username=" + username + ", password=***]";
    }
}
