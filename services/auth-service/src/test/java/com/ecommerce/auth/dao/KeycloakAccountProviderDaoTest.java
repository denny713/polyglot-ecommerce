package com.ecommerce.auth.dao;

import com.ecommerce.auth.configuration.KeycloakAuthProperties;
import com.ecommerce.auth.dao.keycloak.KeycloakAccountProviderDao;
import com.ecommerce.auth.dao.keycloak.KeycloakAdminClient;
import com.ecommerce.auth.dao.keycloak.KeycloakAdminTokenProvider;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakCredentialRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakRoleRepresentation;
import com.ecommerce.auth.dao.keycloak.dto.KeycloakUserRepresentation;
import com.ecommerce.auth.exception.AccountAlreadyExistsException;
import com.ecommerce.auth.exception.AccountNotFoundException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidAccountDataException;
import com.ecommerce.auth.mapper.KeycloakUserMapper;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountRole;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.RawPassword;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for the account management DAO. */
class KeycloakAccountProviderDaoTest {

    private static final String ACCOUNT_ID = "8f1a5c2e-6b3d-4f7a-9e21-0c4d8b5a7f36";
    private static final String BEARER = "Bearer service-account-token";

    private static final NewAccount NEW_ACCOUNT =
            new NewAccount("denny.afrizal", "denny@mail.com", "Denny", "Afrizal");

    private static final RawPassword PASSWORD = new RawPassword("K7mQ2x#9");

    private static final String ROLE_ID = "3c9e1b47-2a5f-4d08-8b6c-1e7a9f2d4c53";

    private KeycloakAdminClient adminClient;
    private KeycloakAccountProviderDao dao;

    @BeforeEach
    void setUp() {
        adminClient = mock(KeycloakAdminClient.class);

        KeycloakAdminTokenProvider tokenProvider = mock(KeycloakAdminTokenProvider.class);
        when(tokenProvider.bearerToken()).thenReturn(BEARER);

        KeycloakAuthProperties properties = mock(KeycloakAuthProperties.class);
        when(properties.realm()).thenReturn("ecommerce");

        dao = new KeycloakAccountProviderDao(adminClient, tokenProvider, properties, new KeycloakUserMapper());
    }

    // ------------------------------------------------------------------
    // create
    // ------------------------------------------------------------------

    @Test
    void shouldCreateTheUserInTheConfiguredRealmWithTheServiceAccountToken() {
        givenTheAccountIsFoundAfterCreation();

        dao.doCreate(NEW_ACCOUNT, PASSWORD);

        verify(adminClient).createUser(eq(BEARER), eq("ecommerce"), any());
    }

    @Test
    void shouldSendTheProfileAndPasswordInASingleCreateCall() {
        givenTheAccountIsFoundAfterCreation();

        dao.doCreate(NEW_ACCOUNT, PASSWORD);

        ArgumentCaptor<KeycloakUserRepresentation> captor =
                ArgumentCaptor.forClass(KeycloakUserRepresentation.class);
        verify(adminClient).createUser(eq(BEARER), eq("ecommerce"), captor.capture());

        KeycloakUserRepresentation sent = captor.getValue();
        assertEquals("denny.afrizal", sent.username());
        assertEquals("denny@mail.com", sent.email());
        assertEquals(1, sent.credentials().size(),
                "a second call to set the password could fail and leave a credential-less account");
    }

    /**
     * The id lives only in a {@code Location} header on the 201, and Keycloak
     * normalizes the username on the way in, so the account has to be read back
     * rather than echoed.
     */
    @Test
    void shouldReturnTheAccountAsKeycloakStoredIt() {
        when(adminClient.findByUsername(BEARER, "ecommerce", "denny.afrizal", true))
                .thenReturn(List.of(new KeycloakUserRepresentation(
                        ACCOUNT_ID, "denny.afrizal", "denny@mail.com", "Denny", "Afrizal",
                        true, false, List.of(), null)));

        Account account = dao.doCreate(NEW_ACCOUNT, PASSWORD);

        assertEquals(ACCOUNT_ID, account.id());
        assertEquals("denny.afrizal", account.username());
    }

    @Test
    void shouldLookTheNewAccountUpByExactUsername() {
        givenTheAccountIsFoundAfterCreation();

        dao.doCreate(NEW_ACCOUNT, PASSWORD);

        // Without exact=true Keycloak runs an infix search and could return
        // somebody else's account whose name merely contains this one.
        verify(adminClient).findByUsername(BEARER, "ecommerce", "denny.afrizal", true);
    }

    @Test
    void shouldReportAnOutageWhenTheCreatedAccountCannotBeReadBack() {
        when(adminClient.findByUsername(any(), any(), any(), eq(true))).thenReturn(List.of());

        assertEquals("The account was created but could not be read back from the identity provider",
                assertThrows(IdentityProviderUnavailableException.class,
                        () -> dao.doCreate(NEW_ACCOUNT, PASSWORD)).getMessage());
    }

    @Test
    void shouldReportAnOutageWhenTheLookupReturnsNothingAtAll() {
        when(adminClient.findByUsername(any(), any(), any(), eq(true))).thenReturn(null);

        assertThrows(IdentityProviderUnavailableException.class, () -> dao.doCreate(NEW_ACCOUNT, PASSWORD));
    }

    @Test
    void shouldRethrowATakenUsernameUntouched() {
        AccountAlreadyExistsException taken = new AccountAlreadyExistsException("taken");
        doThrow(taken).when(adminClient).createUser(any(), any(), any());

        assertSame(taken, assertThrows(AccountAlreadyExistsException.class, () -> dao.doCreate(NEW_ACCOUNT, PASSWORD)));
        verify(adminClient, never()).findByUsername(any(), any(), any(), anyBoolean());
    }

    @Test
    void shouldTranslateTransportFailuresOnCreateIntoAnOutage() {
        ProcessingException transportFailure = new ProcessingException("connection refused");
        doThrow(transportFailure).when(adminClient).createUser(any(), any(), any());

        IdentityProviderUnavailableException thrown = assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.doCreate(NEW_ACCOUNT, PASSWORD));

        assertEquals("Could not reach the identity provider", thrown.getMessage());
        assertSame(transportFailure, thrown.getCause());
    }

    @Test
    void shouldTranslateTransportFailuresOnTheReadBackIntoAnOutage() {
        when(adminClient.findByUsername(any(), any(), any(), eq(true)))
                .thenThrow(new ProcessingException("timeout"));

        assertThrows(IdentityProviderUnavailableException.class, () -> dao.doCreate(NEW_ACCOUNT, PASSWORD));
    }

    // ------------------------------------------------------------------
    // assignRole
    // ------------------------------------------------------------------

    @Test
    void shouldLookTheRealmRoleUpByNameWithTheServiceAccountToken() {
        givenTheRealmHasTheUserRole();

        dao.doAssignRole(ACCOUNT_ID, AccountRole.USER);

        verify(adminClient).findRealmRole(BEARER, "ecommerce", "user");
    }

    /**
     * Keycloak resolves the roles in a mapping by id, not by name, which is the
     * whole reason for the lookup above — sending the name alone answers 404.
     */
    @Test
    void shouldMapTheRoleOntoTheAccountByTheIdTheRealmReturned() {
        givenTheRealmHasTheUserRole();

        dao.doAssignRole(ACCOUNT_ID, AccountRole.USER);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KeycloakRoleRepresentation>> captor = ArgumentCaptor.forClass(List.class);
        verify(adminClient).addRealmRoles(eq(BEARER), eq("ecommerce"), eq(ACCOUNT_ID), captor.capture());

        assertEquals(1, captor.getValue().size());
        assertEquals(ROLE_ID, captor.getValue().get(0).id());
        assertEquals("user", captor.getValue().get(0).name());
    }

    @Test
    void shouldReportAnOutageWhenTheRealmReturnsNoRole() {
        when(adminClient.findRealmRole(any(), any(), any())).thenReturn(null);

        assertEquals("The identity provider did not return the 'user' role",
                assertThrows(IdentityProviderUnavailableException.class,
                        () -> dao.doAssignRole(ACCOUNT_ID, AccountRole.USER)).getMessage());
        verify(adminClient, never()).addRealmRoles(any(), any(), any(), any());
    }

    /**
     * A role without an id is unusable in a mapping, so it is treated exactly
     * like no role at all rather than sent on to be refused.
     */
    @Test
    void shouldReportAnOutageWhenTheRoleComesBackWithoutAnId() {
        when(adminClient.findRealmRole(any(), any(), any()))
                .thenReturn(new KeycloakRoleRepresentation(null, "user"));

        assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.doAssignRole(ACCOUNT_ID, AccountRole.USER));
        verify(adminClient, never()).addRealmRoles(any(), any(), any(), any());
    }

    /**
     * A realm without the role was not provisioned by
     * {@code app/init/keycloak-init.sh}; the mapper has already turned the 404
     * into this, and it must arrive as it is.
     */
    @Test
    void shouldRethrowAMissingRoleUntouched() {
        AccountNotFoundException missing = new AccountNotFoundException("Could not find role");
        when(adminClient.findRealmRole(any(), any(), any())).thenThrow(missing);

        assertSame(missing, assertThrows(AccountNotFoundException.class,
                () -> dao.doAssignRole(ACCOUNT_ID, AccountRole.USER)));
    }

    @Test
    void shouldRethrowAMissingAccountOnRoleMapping() {
        givenTheRealmHasTheUserRole();
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        doThrow(missing).when(adminClient).addRealmRoles(any(), any(), any(), any());

        assertSame(missing, assertThrows(AccountNotFoundException.class,
                () -> dao.doAssignRole(ACCOUNT_ID, AccountRole.USER)));
    }

    @Test
    void shouldTranslateTransportFailuresOnRoleMappingIntoAnOutage() {
        givenTheRealmHasTheUserRole();
        ProcessingException transportFailure = new ProcessingException("connection refused");
        doThrow(transportFailure).when(adminClient).addRealmRoles(any(), any(), any(), any());

        IdentityProviderUnavailableException thrown = assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.doAssignRole(ACCOUNT_ID, AccountRole.USER));

        assertEquals("Could not reach the identity provider", thrown.getMessage());
        assertSame(transportFailure, thrown.getCause());
    }

    @Test
    void shouldTranslateTransportFailuresOnTheRoleLookupIntoAnOutage() {
        when(adminClient.findRealmRole(any(), any(), any())).thenThrow(new ProcessingException("timeout"));

        assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.doAssignRole(ACCOUNT_ID, AccountRole.USER));
    }

    /**
     * The role name on the wire comes from the enum, so a rename of the realm
     * role is a one-line change there rather than a string hunt.
     */
    @Test
    void shouldUseTheRoleNameTheEnumCarries() {
        when(adminClient.findRealmRole(BEARER, "ecommerce", "admin"))
                .thenReturn(new KeycloakRoleRepresentation("role-admin", "admin"));

        dao.doAssignRole(ACCOUNT_ID, AccountRole.ADMIN);

        verify(adminClient).findRealmRole(BEARER, "ecommerce", AccountRole.ADMIN.roleName());
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    void shouldSendOnlyTheChangedFieldsOnUpdate() {
        dao.doUpdate(ACCOUNT_ID, new AccountUpdate("baru@mail.com", null, null));

        ArgumentCaptor<KeycloakUserRepresentation> captor =
                ArgumentCaptor.forClass(KeycloakUserRepresentation.class);
        verify(adminClient).updateUser(eq(BEARER), eq("ecommerce"), eq(ACCOUNT_ID), captor.capture());

        assertEquals("baru@mail.com", captor.getValue().email());
        assertNull(captor.getValue().firstName(), "an omitted field must not reach the wire");
    }

    @Test
    void shouldRethrowAMissingAccountOnUpdate() {
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        doThrow(missing).when(adminClient).updateUser(any(), any(), any(), any());

        assertSame(missing, assertThrows(AccountNotFoundException.class,
                () -> dao.doUpdate(ACCOUNT_ID, new AccountUpdate("baru@mail.com", null, null))));
    }

    @Test
    void shouldRethrowAlreadyTranslatedOutagesOnUpdate() {
        IdentityProviderUnavailableException down = new IdentityProviderUnavailableException("HTTP 500");
        doThrow(down).when(adminClient).updateUser(any(), any(), any(), any());

        assertSame(down, assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.doUpdate(ACCOUNT_ID, new AccountUpdate(null, "Denny", null))));
    }

    @Test
    void shouldTranslateUnmappedHttpFailuresOnUpdateIntoAnOutage() {
        doThrow(new WebApplicationException("unreadable body"))
                .when(adminClient).updateUser(any(), any(), any(), any());

        assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.doUpdate(ACCOUNT_ID, new AccountUpdate(null, null, "Afrizal")));
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    @Test
    void shouldDeleteTheUserFromTheConfiguredRealm() {
        dao.doDelete(ACCOUNT_ID);

        verify(adminClient).deleteUser(BEARER, "ecommerce", ACCOUNT_ID);
    }

    @Test
    void shouldRethrowAMissingAccountOnDelete() {
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        doThrow(missing).when(adminClient).deleteUser(any(), any(), any());

        assertSame(missing, assertThrows(AccountNotFoundException.class, () -> dao.doDelete(ACCOUNT_ID)));
    }

    @Test
    void shouldTranslateTransportFailuresOnDeleteIntoAnOutage() {
        doThrow(new ProcessingException("connection refused"))
                .when(adminClient).deleteUser(any(), any(), any());

        assertThrows(IdentityProviderUnavailableException.class, () -> dao.doDelete(ACCOUNT_ID));
    }

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    void shouldReadAnAccountBackById() {
        when(adminClient.findById(BEARER, "ecommerce", ACCOUNT_ID))
                .thenReturn(new KeycloakUserRepresentation(
                        ACCOUNT_ID, "denny.afrizal", "denny@mail.com", "Denny", "Afrizal",
                        true, false, List.of(), null));

        Account account = dao.doFindById(ACCOUNT_ID);

        assertEquals(ACCOUNT_ID, account.id());
        assertEquals("denny.afrizal", account.username(),
                "the username is what a password check needs, and only the provider knows it");
    }

    @Test
    void shouldRethrowAMissingAccountOnLookup() {
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        when(adminClient.findById(any(), any(), any())).thenThrow(missing);

        assertSame(missing, assertThrows(AccountNotFoundException.class, () -> dao.doFindById(ACCOUNT_ID)));
    }

    @Test
    void shouldTranslateTransportFailuresOnLookupIntoAnOutage() {
        when(adminClient.findById(any(), any(), any())).thenThrow(new ProcessingException("timeout"));

        assertThrows(IdentityProviderUnavailableException.class, () -> dao.doFindById(ACCOUNT_ID));
    }

    // ------------------------------------------------------------------
    // changePassword
    // ------------------------------------------------------------------

    @Test
    void shouldResetThePasswordOfTheAccountInTheConfiguredRealm() {
        dao.doChangePassword(ACCOUNT_ID, new RawPassword("Secret#2026"));

        ArgumentCaptor<KeycloakCredentialRepresentation> captor =
                ArgumentCaptor.forClass(KeycloakCredentialRepresentation.class);
        verify(adminClient).resetPassword(eq(BEARER), eq("ecommerce"), eq(ACCOUNT_ID), captor.capture());

        assertEquals("password", captor.getValue().type());
        assertEquals("Secret#2026", captor.getValue().value());
        assertEquals(Boolean.FALSE, captor.getValue().temporary());
    }

    /**
     * The realm password policy is applied by Keycloak on reset just as on
     * create, and the mapper turns its 400 into this.
     */
    @Test
    void shouldRethrowAPasswordTheRealmPolicyRefused() {
        InvalidAccountDataException refused = new InvalidAccountDataException("Password policy not met");
        doThrow(refused).when(adminClient).resetPassword(any(), any(), any(), any());

        assertSame(refused, assertThrows(InvalidAccountDataException.class,
                () -> dao.doChangePassword(ACCOUNT_ID, new RawPassword("lemah"))));
    }

    @Test
    void shouldRethrowAMissingAccountOnPasswordChange() {
        doThrow(new AccountNotFoundException("gone"))
                .when(adminClient).resetPassword(any(), any(), any(), any());

        assertThrows(AccountNotFoundException.class,
                () -> dao.doChangePassword(ACCOUNT_ID, new RawPassword("Secret#2026")));
    }

    @Test
    void shouldTranslateTransportFailuresOnPasswordChangeIntoAnOutage() {
        doThrow(new ProcessingException("connection refused"))
                .when(adminClient).resetPassword(any(), any(), any(), any());

        assertThrows(IdentityProviderUnavailableException.class,
                () -> dao.doChangePassword(ACCOUNT_ID, new RawPassword("Secret#2026")));
    }

    private void givenTheAccountIsFoundAfterCreation() {
        when(adminClient.findByUsername(BEARER, "ecommerce", "denny.afrizal", true))
                .thenReturn(List.of(new KeycloakUserRepresentation(
                        ACCOUNT_ID, "denny.afrizal", "denny@mail.com", "Denny", "Afrizal",
                        true, false, List.of(), null)));
    }

    private void givenTheRealmHasTheUserRole() {
        when(adminClient.findRealmRole(BEARER, "ecommerce", "user"))
                .thenReturn(new KeycloakRoleRepresentation(ROLE_ID, "user"));
    }
}
