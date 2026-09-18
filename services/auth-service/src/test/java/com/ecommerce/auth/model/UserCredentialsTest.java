package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the credentials value object. */
class UserCredentialsTest {

    @Test
    void shouldAcceptValidCredentials() {
        UserCredentials credentials = new UserCredentials("adminapp", "P@ssw0rd");

        assertEquals("adminapp", credentials.username());
        assertEquals("P@ssw0rd", credentials.password());
    }

    @Test
    void shouldRejectANullUsername() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new UserCredentials(null, "P@ssw0rd"));

        assertEquals("username must not be blank", thrown.getMessage());
    }

    @Test
    void shouldRejectABlankUsername() {
        assertThrows(IllegalArgumentException.class, () -> new UserCredentials("   ", "P@ssw0rd"));
    }

    @Test
    void shouldRejectANullPassword() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new UserCredentials("adminapp", null));

        assertEquals("password must not be empty", thrown.getMessage());
    }

    @Test
    void shouldRejectAnEmptyPassword() {
        assertThrows(IllegalArgumentException.class, () -> new UserCredentials("adminapp", ""));
    }

    /**
     * A password made only of spaces is still a password the user typed — rejecting
     * it here would turn a Keycloak "invalid credentials" into a 400, so only the
     * empty string is refused.
     */
    @Test
    void shouldAcceptAWhitespacePassword() {
        assertEquals("   ", new UserCredentials("adminapp", "   ").password());
    }

    @Test
    void shouldNeverPrintThePassword() {
        String printed = new UserCredentials("adminapp", "P@ssw0rd").toString();

        assertFalse(printed.contains("P@ssw0rd"), "the password leaked into toString()");
        assertTrue(printed.contains("adminapp"), "the username is safe to log and helps auditing");
        assertEquals("UserCredentials[username=adminapp, password=***]", printed);
    }
}