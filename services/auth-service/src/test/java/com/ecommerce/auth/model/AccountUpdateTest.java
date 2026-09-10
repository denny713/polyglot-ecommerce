package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AccountUpdate#isEmpty()} is what stops a no-op request from costing a
 * round trip to Keycloak, so each field has to count on its own.
 */
class AccountUpdateTest {

    @Test
    void shouldBeEmptyWhenNoFieldIsSet() {
        assertTrue(new AccountUpdate(null, null, null).isEmpty());
    }

    @Test
    void shouldNotBeEmptyWhenOnlyTheEmailIsSet() {
        assertFalse(new AccountUpdate("denny@mail.com", null, null).isEmpty());
    }

    @Test
    void shouldNotBeEmptyWhenOnlyTheFirstNameIsSet() {
        assertFalse(new AccountUpdate(null, "Denny", null).isEmpty());
    }

    @Test
    void shouldNotBeEmptyWhenOnlyTheLastNameIsSet() {
        assertFalse(new AccountUpdate(null, null, "Afrizal").isEmpty());
    }

    /**
     * An empty string is a value, not an absence — it is Bean Validation's job to
     * refuse it, not this record's job to pretend it was never sent.
     */
    @Test
    void shouldTreatAnEmptyStringAsAValue() {
        assertFalse(new AccountUpdate("", null, null).isEmpty());
    }
}
