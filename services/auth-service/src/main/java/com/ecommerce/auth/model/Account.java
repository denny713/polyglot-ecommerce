package com.ecommerce.auth.model;

/**
 * An account as it exists in the identity provider, in a form that is neutral
 * with respect to which provider that is.
 */
public record Account(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        boolean enabled) {
}
