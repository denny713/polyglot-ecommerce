package com.ecommerce.auth.mapper;

import com.ecommerce.auth.dao.keycloak.dto.KeycloakCredentialRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakUserRepresentation;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.RawPassword;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The translation between the account domain models and Keycloak's {@code UserRepresentation}. */
class KeycloakUserMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final NewAccount NEW_ACCOUNT =
            new NewAccount("denny.afrizal", "denny@mail.com", "Denny", "Afrizal");

    private static final RawPassword PASSWORD = new RawPassword("K7mQ2x#9");

    private KeycloakUserMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new KeycloakUserMapper();
    }

    // ------------------------------------------------------------------
    // NewAccount -> representation
    // ------------------------------------------------------------------

    @Test
    void shouldCopyTheProfileOfANewAccount() {
        KeycloakUserRepresentation representation = mapper.toRepresentation(
                NEW_ACCOUNT, PASSWORD);

        assertEquals("denny.afrizal", representation.username());
        assertEquals("denny@mail.com", representation.email());
        assertEquals("Denny", representation.firstName());
        assertEquals("Afrizal", representation.lastName());
    }

    @Test
    void shouldSendTheIdAsNullSoKeycloakAssignsIt() {
        assertNull(mapper.toRepresentation(
                NEW_ACCOUNT, PASSWORD).id());
    }

    @Test
    void shouldEnableANewAccountImmediately() {
        KeycloakUserRepresentation representation = mapper.toRepresentation(
                NEW_ACCOUNT, PASSWORD);

        assertEquals(Boolean.TRUE, representation.enabled());
        assertEquals(List.of(), representation.requiredActions(),
                "a pending required action would stop the user at a setup screen on first login");
    }

    /** Nothing has verified the address, and claiming otherwise is a lie the rest of the platform would then trust. */
    @Test
    void shouldClaimTheEmailIsVerified() {
        assertEquals(Boolean.TRUE, mapper.toRepresentation(
                NEW_ACCOUNT, PASSWORD).emailVerified());
    }

    @Test
    void shouldSendThePasswordAsAPermanentCredential() {
        List<KeycloakCredentialRepresentation> credentials = mapper.toRepresentation(
                NEW_ACCOUNT, PASSWORD).credentials();

        assertEquals(1, credentials.size());
        assertEquals("password", credentials.get(0).type());
        assertEquals("K7mQ2x#9", credentials.get(0).value());
        assertEquals(Boolean.FALSE, credentials.get(0).temporary(),
                "a temporary password would force a reset at first login");
    }

    @Test
    void shouldRejectANullNewAccount() {
        assertEquals("newAccount must not be null", assertThrows(IllegalArgumentException.class,
                () -> mapper.toRepresentation(null, PASSWORD)).getMessage());
    }

    // ------------------------------------------------------------------
    // AccountUpdate -> representation
    // ------------------------------------------------------------------

    @Test
    void shouldCopyOnlyTheFieldsAnUpdateChanges() {
        KeycloakUserRepresentation representation =
                mapper.toRepresentation(new AccountUpdate("baru@mail.com", null, null));

        assertEquals("baru@mail.com", representation.email());
        assertNull(representation.firstName());
        assertNull(representation.lastName());
    }

    /**
     * The dangerous ones. Sending {@code enabled} at all would let a profile edit
     * disable an account, or re-enable one an administrator disabled on purpose;
     * sending {@code username} or {@code credentials} would let it rename the
     * account or reset the password.
     */
    @Test
    void shouldNeverSendIdentityOrCredentialFieldsOnAnUpdate() {
        KeycloakUserRepresentation representation =
                mapper.toRepresentation(new AccountUpdate("baru@mail.com", "Denny", "Afrizal"));

        assertNull(representation.id());
        assertNull(representation.username());
        assertNull(representation.enabled());
        assertNull(representation.emailVerified());
        assertNull(representation.requiredActions());
        assertNull(representation.credentials());
    }

    /**
     * The assertion the whole partial-update design rests on: an omitted field
     * must not reach the wire at all, because {@code "email": null} would ask
     * Keycloak to clear the address rather than keep it.
     */
    @Test
    void shouldSerializeAnUpdateWithoutItsAbsentFields() throws Exception {
        String body = JSON.writeValueAsString(mapper.toRepresentation(new AccountUpdate(null, "Denny", null)));

        assertEquals("{\"firstName\":\"Denny\"}", body);
        assertFalse(body.contains("email"));
        assertFalse(body.contains("null"));
    }

    @Test
    void shouldRejectANullPasswordOnCreate() {
        assertEquals("password must not be null", assertThrows(IllegalArgumentException.class,
                () -> mapper.toRepresentation(NEW_ACCOUNT, null)).getMessage());
    }

    // ------------------------------------------------------------------
    // RawPassword -> credential
    // ------------------------------------------------------------------

    @Test
    void shouldBuildAPermanentCredentialForAPasswordChange() {
        KeycloakCredentialRepresentation credential = mapper.toCredential(new RawPassword("Secret#2026"));

        assertEquals("password", credential.type());
        assertEquals("Secret#2026", credential.value());
        assertEquals(Boolean.FALSE, credential.temporary(),
                "the holder just chose this value; making them choose again would be an obstacle");
    }

    @Test
    void shouldRejectANullPasswordOnChange() {
        assertEquals("password must not be null", assertThrows(IllegalArgumentException.class,
                () -> mapper.toCredential(null)).getMessage());
    }

    @Test
    void shouldRejectANullUpdate() {
        assertEquals("update must not be null", assertThrows(IllegalArgumentException.class,
                () -> mapper.toRepresentation((AccountUpdate) null)).getMessage());
    }

    // ------------------------------------------------------------------
    // representation -> Account
    // ------------------------------------------------------------------

    @Test
    void shouldReadTheStoredAccountBackIntoTheDomain() {
        Account account = mapper.toDomain(new KeycloakUserRepresentation(
                "8f1a5c2e", "denny.afrizal", "denny@mail.com", "Denny", "Afrizal",
                true, false, List.of(), null));

        assertEquals("8f1a5c2e", account.id());
        assertEquals("denny.afrizal", account.username());
        assertEquals("denny@mail.com", account.email());
        assertEquals("Denny", account.firstName());
        assertEquals("Afrizal", account.lastName());
        assertTrue(account.enabled());
    }

    /**
     * Keycloak may leave {@code enabled} out of a representation entirely. Absent
     * must read as "not enabled" rather than blow up on unboxing.
     */
    @Test
    void shouldTreatAnAbsentEnabledFlagAsDisabled() {
        assertFalse(mapper.toDomain(new KeycloakUserRepresentation(
                "8f1a5c2e", "denny.afrizal", "denny@mail.com", "Denny", "Afrizal",
                null, null, null, null)).enabled());
    }

    @Test
    void shouldReportADisabledAccountAsDisabled() {
        assertFalse(mapper.toDomain(new KeycloakUserRepresentation(
                "8f1a5c2e", "denny.afrizal", "denny@mail.com", "Denny", "Afrizal",
                false, false, null, null)).enabled());
    }

    @Test
    void shouldRejectANullRepresentation() {
        assertEquals("Keycloak user representation must not be null", assertThrows(
                IllegalArgumentException.class, () -> mapper.toDomain(null)).getMessage());
    }

    // ------------------------------------------------------------------
    // credential representation
    // ------------------------------------------------------------------

    @Test
    void shouldNeverPrintACredentialValue() {
        String printed = KeycloakCredentialRepresentation.permanentPassword("K7mQ2x#9").toString();

        assertFalse(printed.contains("K7mQ2x#9"), "toString leaked the password: " + printed);
        assertTrue(printed.contains("value=***"));
    }
}
