package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The invariants that make {@link NewAccount} safe to pass around: it cannot be built half-empty. */
class NewAccountTest {

    private static NewAccount valid() {
        return new NewAccount("denny.afrizal", "denny@mail.com", "Denny", "Afrizal");
    }

    @Test
    void shouldExposeEveryFieldItWasBuiltWith() {
        NewAccount account = valid();

        assertEquals("denny.afrizal", account.username());
        assertEquals("denny@mail.com", account.email());
        assertEquals("Denny", account.firstName());
        assertEquals("Afrizal", account.lastName());
    }

    @Test
    void shouldRejectABlankUsername() {
        assertEquals("username must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new NewAccount("  ", "denny@mail.com", "Denny", "Afrizal")).getMessage());
    }

    @Test
    void shouldRejectANullUsername() {
        assertThrows(IllegalArgumentException.class,
                () -> new NewAccount(null, "denny@mail.com", "Denny", "Afrizal"));
    }

    /** The address is where the generated password goes, so an account without one could never be logged in to. */
    @Test
    void shouldRejectABlankEmail() {
        assertEquals("email must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new NewAccount("denny.afrizal", " ", "Denny", "Afrizal")).getMessage());
    }

    @Test
    void shouldRejectANullEmail() {
        assertThrows(IllegalArgumentException.class,
                () -> new NewAccount("denny.afrizal", null, "Denny", "Afrizal"));
    }

    /**
     * Not cosmetic: Keycloak 26 rejects a login with "Account is not fully set
     * up" when the profile is incomplete, so an account created without a first
     * name is created successfully and then unusable.
     */
    @Test
    void shouldRejectABlankFirstName() {
        assertEquals("firstName must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new NewAccount("denny.afrizal", "denny@mail.com", "", "Afrizal")).getMessage());
    }

    @Test
    void shouldRejectABlankLastName() {
        assertEquals("lastName must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new NewAccount("denny.afrizal", "denny@mail.com", "Denny", "\t")).getMessage());
    }
}
