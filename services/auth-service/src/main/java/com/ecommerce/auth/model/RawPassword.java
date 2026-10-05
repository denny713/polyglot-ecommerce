package com.ecommerce.auth.model;

/**
 * A password in plaintext, on its way to the identity provider.
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
