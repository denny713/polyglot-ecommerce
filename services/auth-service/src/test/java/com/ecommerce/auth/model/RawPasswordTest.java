package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The masking guarantee is the entire reason this type exists — a generated
 * password passes through the service, the notifier and the DAO, and every one
 * of those logs something.
 */
class RawPasswordTest {

    @Test
    void shouldKeepTheValueAvailableToCodeThatNeedsIt() {
        assertEquals("K7mQ2x#9", new RawPassword("K7mQ2x#9").value());
    }

    @Test
    void shouldNeverPrintTheValue() {
        String printed = new RawPassword("K7mQ2x#9").toString();

        assertFalse(printed.contains("K7mQ2x#9"), "toString leaked the password: " + printed);
        assertTrue(printed.contains("value=***"));
    }

    @Test
    void shouldRejectANullValue() {
        assertEquals("password must not be empty",
                assertThrows(IllegalArgumentException.class, () -> new RawPassword(null)).getMessage());
    }

    @Test
    void shouldRejectAnEmptyValue() {
        assertThrows(IllegalArgumentException.class, () -> new RawPassword(""));
    }

    /**
     * Whitespace is a legitimate password as far as this record is concerned.
     * Whether it is strong enough is the realm policy's call, and duplicating
     * that judgement here would give it two places to change.
     */
    @Test
    void shouldLeavePasswordStrengthToTheRealmPolicy() {
        assertEquals("   ", new RawPassword("   ").value());
    }
}
