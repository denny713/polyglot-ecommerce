package com.ecommerce.auth.model;

/**
 * An account as it exists in the identity provider, in a form that is neutral
 * with respect to which provider that is.
 *
 * <p>
 * No password field: this service never reads one back, and Keycloak would not
 * hand one over — the {@code CREDENTIAL} table holds a PBKDF2 hash, not a
 * password. The {@link #id()} is the provider's own identifier, which is also
 * the {@code sub} claim of every token issued for this account, and it is what
 * every endpoint under {@code /api/account} resolves "me" to.
 */
public record Account(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled) {
}
