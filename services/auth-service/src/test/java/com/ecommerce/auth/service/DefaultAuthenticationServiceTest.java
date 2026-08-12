package com.ecommerce.auth.service;

import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.dao.SessionTerminationDao;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.UserCredentials;
import com.ecommerce.auth.service.impl.DefaultAuthenticationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the business layer.
 *
 * <p>
 * No {@code @QuarkusTest} here on purpose: {@link DefaultAuthenticationService}
 * takes its collaborators through the constructor, so it can be built by hand and
 * the whole class runs in milliseconds without a CDI container. That is the
 * payoff of constructor injection.
 */
class DefaultAuthenticationServiceTest {

    private static final UserCredentials CREDENTIALS = new UserCredentials("adminapp", "P@ssw0rd");
    private static final RefreshToken REFRESH_TOKEN = new RefreshToken("refresh-token");

    private IdentityProviderDao identityProviderDao;
    private SessionTerminationDao sessionTerminationDao;
    private DefaultAuthenticationService service;

    @BeforeEach
    void setUp() {
        identityProviderDao = mock(IdentityProviderDao.class);
        sessionTerminationDao = mock(SessionTerminationDao.class);
        service = new DefaultAuthenticationService(identityProviderDao, sessionTerminationDao);
    }

    // ------------------------------------------------------------------
    // doLogin
    // ------------------------------------------------------------------

    @Test
    void shouldReturnTheTokenIssuedByTheIdentityProvider() {
        AuthToken issued = new AuthToken("access", "refresh", "Bearer", 300, 1800, "profile");
        when(identityProviderDao.authenticate(CREDENTIALS)).thenReturn(issued);

        AuthToken result = service.doLogin(CREDENTIALS);

        assertSame(issued, result, "the service must not rebuild the token");
        verify(identityProviderDao).authenticate(CREDENTIALS);
        verifyNoInteractions(sessionTerminationDao);
    }

    @Test
    void shouldRethrowAuthenticationFailuresUnchanged() {
        InvalidCredentialsException rejected = new InvalidCredentialsException("Invalid username or password");
        when(identityProviderDao.authenticate(CREDENTIALS)).thenThrow(rejected);

        InvalidCredentialsException thrown = assertThrows(InvalidCredentialsException.class,
                () -> service.doLogin(CREDENTIALS));

        // Same instance: logging a failed login must not swallow or repackage it.
        assertSame(rejected, thrown);
    }

    @Test
    void shouldRethrowAccountLockedFailures() {
        when(identityProviderDao.authenticate(CREDENTIALS)).thenThrow(new AccountLockedException("locked"));

        assertThrows(AccountLockedException.class, () -> service.doLogin(CREDENTIALS));
    }

    @Test
    void shouldPropagateIdentityProviderOutages() {
        when(identityProviderDao.authenticate(CREDENTIALS))
                .thenThrow(new IdentityProviderUnavailableException("down"));

        assertThrows(IdentityProviderUnavailableException.class, () -> service.doLogin(CREDENTIALS));
    }

    @Test
    void shouldRejectNullCredentialsBeforeCallingTheIdentityProvider() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.doLogin(null));

        assertEquals("credentials must not be null", thrown.getMessage());
        verifyNoInteractions(identityProviderDao);
    }

    // ------------------------------------------------------------------
    // doLogout
    // ------------------------------------------------------------------

    @Test
    void shouldRevokeTheRefreshTokenOnLogout() {
        service.doLogout(REFRESH_TOKEN);

        verify(sessionTerminationDao).revoke(REFRESH_TOKEN);
        verifyNoInteractions(identityProviderDao);
    }

    /**
     * The business rule that makes logout idempotent — a session that has already
     * ended is exactly the state the caller asked for.
     */
    @Test
    void shouldTreatAnAlreadyEndedSessionAsASuccessfulLogout() {
        doThrow(new InvalidRefreshTokenException("expired"))
                .when(sessionTerminationDao).revoke(REFRESH_TOKEN);

        service.doLogout(REFRESH_TOKEN);

        verify(sessionTerminationDao).revoke(REFRESH_TOKEN);
    }

    /**
     * The counterpart of the rule above: swallowing a dead session must not turn
     * into swallowing a broken Keycloak, or a client would believe it is logged out
     * while its session is still live.
     */
    @Test
    void shouldPropagateIdentityProviderOutagesOnLogout() {
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(sessionTerminationDao).revoke(REFRESH_TOKEN);

        assertThrows(IdentityProviderUnavailableException.class, () -> service.doLogout(REFRESH_TOKEN));
    }

    @Test
    void shouldRejectNullRefreshTokenBeforeCallingTheIdentityProvider() {
        NullPointerException thrown = assertThrows(NullPointerException.class, () -> service.doLogout(null));

        assertEquals("refreshToken must not be null", thrown.getMessage());
        verifyNoInteractions(sessionTerminationDao);
    }
}