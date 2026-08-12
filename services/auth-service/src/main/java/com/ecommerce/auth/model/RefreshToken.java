package com.ecommerce.auth.model;

/**
 * The refresh token presented when ending a session.
 *
 * <p>
 * Wrapped in a record rather than passed around as a bare {@code String} for two
 * reasons: the "must not be blank" rule lives in one place, and
 * {@link #toString()} can guarantee the token is never printed to the logs — a
 * refresh token is as sensitive as a password, since anyone holding it can mint
 * new access tokens.
 *
 * <p>
 * Like {@link UserCredentials}, this is a domain model rather than a JPA entity:
 * the session it refers to is owned by Keycloak, not by this service.
 */
public record RefreshToken(String value) {

    public RefreshToken {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("refresh token must not be blank");
        }
    }

    @Override
    public String toString() {
        return "RefreshToken[value=***]";
    }
}