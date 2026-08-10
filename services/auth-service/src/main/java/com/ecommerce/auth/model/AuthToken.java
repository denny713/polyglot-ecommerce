package com.ecommerce.auth.model;

/**
 * Token hasil autentikasi, dalam bentuk yang netral terhadap identity provider.
 *
 * <p>
 * Model domain ini sengaja tidak memakai penamaan snake_case milik OAuth2 /
 * Keycloak. Dengan begitu layer service tidak ikut berubah kalau suatu saat
 * provider-nya diganti (Open/Closed Principle).
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
