package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakTokenResponse;
import com.ecommerce.auth.model.AuthToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for the Keycloak payload → domain mapping.
 *
 * <p>
 * This is the boundary where OAuth2's {@code expires_in} / {@code refresh_expires_in}
 * become the domain's {@code expiresInSeconds} / {@code refreshExpiresInSeconds}.
 * The two are easy to transpose and impossible to notice afterwards, which is the
 * whole reason these assertions use distinct values.
 */
class KeycloakTokenMapperTest {

    private final KeycloakTokenMapper mapper = new KeycloakTokenMapper();

    @Test
    void shouldMapEveryFieldOntoTheDomainModel() {
        AuthToken token = mapper.toDomain(new KeycloakTokenResponse(
                "access-token", "refresh-token", "Bearer", 300, 1800, "profile email"));

        assertEquals("access-token", token.accessToken());
        assertEquals("refresh-token", token.refreshToken());
        assertEquals("Bearer", token.tokenType());
        assertEquals(300L, token.expiresInSeconds());
        assertEquals(1800L, token.refreshExpiresInSeconds());
        assertEquals("profile email", token.scope());
    }

    @Test
    void shouldNotTransposeTheTwoLifetimes() {
        AuthToken token = mapper.toDomain(new KeycloakTokenResponse(
                "a", "r", "Bearer", 60, 3600, "s"));

        assertEquals(60L, token.expiresInSeconds());
        assertEquals(3600L, token.refreshExpiresInSeconds());
    }

    @Test
    void shouldPassNullScopeThrough() {
        AuthToken token = mapper.toDomain(new KeycloakTokenResponse(
                "access-token", "refresh-token", "Bearer", 300, 1800, null));

        assertNull(token.scope());
    }

    /**
     * A null here would mean Keycloak answered 200 with an empty body — better to
     * fail loudly than to hand a token full of nulls to the caller.
     */
    @Test
    void shouldRejectANullResponse() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mapper.toDomain(null));

        assertEquals("Keycloak token response must not be null", thrown.getMessage());
    }
}