package com.ecommerce.auth.dto.response;

/**
 * Success response body for {@code POST /api/auth/login}.
 *
 * @param accessToken      JWT to be sent as {@code Authorization: Bearer ...}
 * @param refreshToken     token used to renew the access token
 * @param tokenType        usually {@code Bearer}
 * @param expiresIn        access token lifetime, in seconds
 * @param refreshExpiresIn refresh token lifetime, in seconds
 * @param scope            the scope actually granted by Keycloak
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        String scope) {
}
