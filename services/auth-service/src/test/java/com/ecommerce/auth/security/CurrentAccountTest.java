package com.ecommerce.auth.security;

import com.ecommerce.auth.enums.AccountErrorCode;
import com.ecommerce.auth.exception.AccountAccessDeniedException;
import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for resolving "me" from the bearer token. */
class CurrentAccountTest {

    private static final String OWN_ID = "8f1a5c2e-6b3d-4f7a-9e21-0c4d8b5a7f36";

    private SecurityIdentity identity;
    private CurrentAccount currentAccount;

    @BeforeEach
    void setUp() {
        identity = mock(SecurityIdentity.class);
        currentAccount = new CurrentAccount(identity);
    }

    /**
     * The {@code sub} claim, not the username: {@code sub} is what Keycloak uses
     * as the account id everywhere else in this service.
     */
    @Test
    void shouldTakeTheAccountIdFromTheSubjectClaim() {
        givenCallerWithSubject(OWN_ID);

        assertEquals(OWN_ID, currentAccount.id());
    }

    @Test
    void shouldRefuseACallerWhosePrincipalIsNotAToken() {
        when(identity.getPrincipal()).thenReturn(mock(Principal.class));

        AccountAccessDeniedException thrown = assertThrows(AccountAccessDeniedException.class,
                () -> currentAccount.id());

        assertEquals("The access token does not identify an account", thrown.getMessage());
        assertEquals(AccountErrorCode.ACCOUNT_ACCESS_DENIED, thrown.errorCode());
    }

    @Test
    void shouldRefuseATokenWithNoSubjectClaim() {
        givenCallerWithSubject(null);

        assertThrows(AccountAccessDeniedException.class, () -> currentAccount.id());
    }

    @Test
    void shouldRefuseATokenWhoseSubjectIsBlank() {
        givenCallerWithSubject("  ");

        assertThrows(AccountAccessDeniedException.class, () -> currentAccount.id());
    }

    /**
     * Nothing here consults a role. Keeping that true is the point: the moment a
     * role could change which account is resolved, the endpoints would be back
     * to needing an authorization check.
     */
    @Test
    void shouldNotConsultRoles() {
        givenCallerWithSubject(OWN_ID);

        currentAccount.id();

        org.mockito.Mockito.verify(identity, org.mockito.Mockito.never())
                .hasRole(org.mockito.ArgumentMatchers.anyString());
    }

    private void givenCallerWithSubject(String subject) {
        JsonWebToken token = mock(JsonWebToken.class);
        when(token.getSubject()).thenReturn(subject);
        when(identity.getPrincipal()).thenReturn(token);
    }
}
