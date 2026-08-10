package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;
import com.ecommerce.auth.model.AuthToken;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mengubah response Keycloak menjadi model domain.
 *
 * <p>
 * Dipisah dari DAO supaya masing-masing punya satu alasan untuk berubah
 * (Single Responsibility Principle): DAO berubah kalau cara memanggilnya
 * berubah, mapper berubah kalau bentuk payload-nya berubah.
 */
@ApplicationScoped
public class KeycloakTokenMapper {

    public AuthToken toDomain(KeycloakTokenResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Keycloak token response must not be null");
        }

        return new AuthToken(
                response.accessToken(),
                response.refreshToken(),
                response.tokenType(),
                response.expiresIn(),
                response.refreshExpiresIn(),
                response.scope());
    }
}
