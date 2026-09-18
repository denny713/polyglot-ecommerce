package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;
import com.ecommerce.auth.model.AuthToken;

import jakarta.enterprise.context.ApplicationScoped;

/** Converts a Keycloak response into the domain model. */
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
