package com.ecommerce.auth.dto.response;

/**
 * Body response sukses {@code POST /api/auth/login}.
 *
 * @param accessToken       JWT untuk dikirim sebagai {@code Authorization: Bearer ...}
 * @param refreshToken      token untuk memperbarui access token
 * @param tokenType         umumnya {@code Bearer}
 * @param expiresIn         masa berlaku access token, dalam detik
 * @param refreshExpiresIn  masa berlaku refresh token, dalam detik
 * @param scope             scope yang benar-benar diberikan Keycloak
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        String scope) {
}
