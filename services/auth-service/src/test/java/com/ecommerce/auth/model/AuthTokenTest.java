package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the token value object. */
class AuthTokenTest {

    @Test
    void shouldExposeEveryFieldItWasBuiltWith() {
        AuthToken token = new AuthToken("access", "refresh", "Bearer", 300, 1800, "profile email");

        assertEquals("access", token.accessToken());
        assertEquals("refresh", token.refreshToken());
        assertEquals("Bearer", token.tokenType());
        assertEquals(300L, token.expiresInSeconds());
        assertEquals(1800L, token.refreshExpiresInSeconds());
        assertEquals("profile email", token.scope());
    }

    /**
     * The token values here deliberately share no substring with the field labels
     * {@code accessToken=} / {@code refreshToken=}, otherwise the assertion would
     * match the label rather than a leaked value.
     */
    @Test
    void shouldNeverPrintEitherToken() {
        String printed = new AuthToken("jwt-payload", "renewal-secret", "Bearer", 300, 1800, "profile email")
                .toString();

        assertFalse(printed.contains("jwt-payload"), "the access token leaked into toString()");
        assertFalse(printed.contains("renewal-secret"), "the refresh token leaked into toString()");
        assertEquals("AuthToken[tokenType=Bearer, expiresInSeconds=300, refreshExpiresInSeconds=1800, "
                + "scope=profile email, accessToken=***, refreshToken=***]", printed);
    }

    @Test
    void shouldStillPrintTheMetadataThatMakesALogLineUseful() {
        String printed = new AuthToken("jwt-payload", "renewal-secret", "Bearer", 300, 1800, "profile").toString();

        assertTrue(printed.contains("tokenType=Bearer"));
        assertTrue(printed.contains("expiresInSeconds=300"));
        assertTrue(printed.contains("refreshExpiresInSeconds=1800"));
        assertTrue(printed.contains("scope=profile"));
    }
}