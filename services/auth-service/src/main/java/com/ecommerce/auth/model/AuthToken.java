package com.ecommerce.auth.model;

/**
 * The token resulting from authentication, in a form that is neutral with respect
 * to the identity provider.
 *
 * <p>
 * This domain model deliberately avoids the snake_case naming used by OAuth2 /
 * Keycloak. That way the service layer does not have to change if the provider is
 * ever swapped out (Open/Closed Principle).
 */
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
