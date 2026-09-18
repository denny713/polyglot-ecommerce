package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Unit tests for the refresh token value object. */
class RefreshTokenTest {

    @Test
    void shouldAcceptANonBlankToken() {
        assertEquals("refresh-token", new RefreshToken("refresh-token").value());
    }

    @Test
    void shouldRejectANullToken() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new RefreshToken(null));

        assertEquals("refresh token must not be blank", thrown.getMessage());
    }

    @Test
    void shouldRejectABlankToken() {
        assertThrows(IllegalArgumentException.class, () -> new RefreshToken("   "));
    }

    @Test
    void shouldNeverPrintTheToken() {
        String printed = new RefreshToken("refresh-token").toString();

        assertFalse(printed.contains("refresh-token"), "the refresh token leaked into toString()");
        assertEquals("RefreshToken[value=***]", printed);
    }

    /**
     * The controller looks the token up by value, and the DAO test verifies the call
     * with an equal-but-not-identical instance, so value equality has to hold.
     */
    @Test
    void shouldCompareByValue() {
        assertEquals(new RefreshToken("same"), new RefreshToken("same"));
        assertEquals(new RefreshToken("same").hashCode(), new RefreshToken("same").hashCode());
        assertNotEquals(new RefreshToken("a"), new RefreshToken("b"));
    }
}