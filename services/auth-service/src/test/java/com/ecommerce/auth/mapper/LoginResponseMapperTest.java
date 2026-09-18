package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dto.response.LoginResponse;
import com.ecommerce.auth.model.AuthToken;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Unit tests for the domain → DTO mapping. */
class LoginResponseMapperTest {

    private final LoginResponseMapper mapper = new LoginResponseMapper();

    @Test
    void shouldCopyEveryFieldIntoTheResponse() {
        LoginResponse response = mapper.toResponse(
                new AuthToken("access-token", "refresh-token", "Bearer", 300, 1800, "profile email"));

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(300L, response.expiresIn());
        assertEquals(1800L, response.refreshExpiresIn());
        assertEquals("profile email", response.scope());
    }

    /**
     * Guards against the accessToken / refreshToken pair being swapped: both are
     * {@code String}, so only distinct values catch it.
     */
    @Test
    void shouldNotSwapTheAccessAndRefreshTokens() {
        LoginResponse response = mapper.toResponse(
                new AuthToken("A", "R", "Bearer", 1, 2, "s"));

        assertEquals("A", response.accessToken());
        assertEquals("R", response.refreshToken());
        assertEquals(1L, response.expiresIn());
        assertEquals(2L, response.refreshExpiresIn());
    }

    /**
     * Keycloak omits {@code scope} when the client requested none, so a null must
     * travel through untouched rather than becoming an empty string.
     */
    @Test
    void shouldPassNullScopeThrough() {
        LoginResponse response = mapper.toResponse(
                new AuthToken("access-token", "refresh-token", "Bearer", 300, 1800, null));

        assertNull(response.scope());
    }
}