package com.ecommerce.auth.model;

/**
 * A password in plaintext, on its way to the identity provider.
 *
 * <p>
 * Wrapped in a record rather than passed around as a bare {@code String} for the
 * same reason as {@link RefreshToken}: {@link #toString()} can then guarantee it
 * never reaches a log line. That matters more here than almost anywhere else in
 * the service — a generated password travels through the account service, the
 * notifier and the DAO before it lands in Keycloak, and every one of those steps
 * logs something.
 *
 * <p>
 * One type covers both the password generated at registration and the one a
 * holder chooses when changing it: from the identity provider's side they are
 * the same thing, and the difference in meaning is already carried by the names
 * of the methods that produce them.
 *
 * <p>
 * Whether the value is strong enough is not judged here. The realm's password
 * policy is the authority on that, and Keycloak applies it to admin-set
 * passwords too; encoding it a second time in this record would give it two
 * places to change.
 *
 * @see com.ecommerce.auth.security.TemporaryPasswordGenerator
 */
public record RawPassword(String value) {

    public RawPassword {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }

    @Override
    public String toString() {
        return "RawPassword[value=***]";
    }
}
