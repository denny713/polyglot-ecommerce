package com.ecommerce.auth.model;

/** The token resulting from authentication, in a form that is neutral with respect to the identity provider. */
public record AuthToken(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        long refreshExpiresInSeconds,
        String scope) {

    @Override
    public String toString() {
        return "AuthToken[tokenType=" + tokenType
                + ", expiresInSeconds=" + expiresInSeconds
                + ", refreshExpiresInSeconds=" + refreshExpiresInSeconds
                + ", scope=" + scope
                + ", accessToken=***, refreshToken=***]";
    }
}
