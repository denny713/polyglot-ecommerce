package com.ecommerce.auth.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordChangeTest {

    @Test
    void shouldExposeBothPasswords() {
        PasswordChange change = new PasswordChange("K7mQ2x#9", "Secret#2026");

        assertEquals("K7mQ2x#9", change.oldPassword());
        assertEquals("Secret#2026", change.newPassword());
    }

    @Test
    void shouldRejectAMissingOldPassword() {
        assertEquals("oldPassword must not be empty", assertThrows(IllegalArgumentException.class,
                () -> new PasswordChange(null, "Secret#2026")).getMessage());
    }

    @Test
    void shouldRejectAnEmptyOldPassword() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordChange("", "Secret#2026"));
    }

    @Test
    void shouldRejectAMissingNewPassword() {
        assertEquals("newPassword must not be empty", assertThrows(IllegalArgumentException.class,
                () -> new PasswordChange("K7mQ2x#9", null)).getMessage());
    }

    /**
     * Re-setting the same value would look like a successful rotation in an
     * audit log while changing nothing.
     */
    @Test
    void shouldRecogniseAChangeThatChangesNothing() {
        assertTrue(new PasswordChange("K7mQ2x#9", "K7mQ2x#9").isNoOp());
    }

    @Test
    void shouldNotCallARealChangeANoOp() {
        assertFalse(new PasswordChange("K7mQ2x#9", "Secret#2026").isNoOp());
    }

    /**
     * Case matters — treating these as equal would refuse a legitimate change.
     */
    @Test
    void shouldCompareThePasswordsExactly() {
        assertFalse(new PasswordChange("K7mQ2x#9", "k7mq2X#9").isNoOp());
    }

    @Test
    void shouldNeverPrintEitherPassword() {
        String printed = new PasswordChange("K7mQ2x#9", "Secret#2026").toString();

        assertFalse(printed.contains("K7mQ2x#9"), "toString leaked the old password: " + printed);
        assertFalse(printed.contains("Secret#2026"), "toString leaked the new password: " + printed);
        assertEquals("PasswordChange[oldPassword=***, newPassword=***]", printed);
    }
}
