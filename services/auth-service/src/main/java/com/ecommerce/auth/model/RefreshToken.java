package com.ecommerce.auth.model;

/** The refresh token presented when ending a session. */
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