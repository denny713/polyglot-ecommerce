package com.ecommerce.auth.security;

import com.ecommerce.auth.dao.IdentityProviderDao;
import com.ecommerce.auth.dao.SessionTerminationDao;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.InvalidRefreshTokenException;
import com.ecommerce.auth.model.AuthToken;
import com.ecommerce.auth.model.RefreshToken;
import com.ecommerce.auth.model.UserCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the "does this caller know the current password?" check.
 *
 * <p>
 * Two behaviours carry the weight. A wrong password has to come back as the
 * authentication failure it is, so the realm's brute force detection stays in
 * the loop and this endpoint cannot be used as an offline oracle; and the
 * session the check opens has to be closed again, or every password change
 * would quietly leave a live refresh token behind that nobody asked for.
 */
class CurrentPasswordVerifierTest {

    private static final AuthToken TOKEN =
            new AuthToken("access", "refresh", "Bearer", 300, 1800, "profile");

    private IdentityProviderDao identityProviderDao;
    private SessionTerminationDao sessionTerminationDao;
    private CurrentPasswordVerifier verifier;

    @BeforeEach
    void setUp() {
        identityProviderDao = mock(IdentityProviderDao.class);
        sessionTerminationDao = mock(SessionTerminationDao.class);
        verifier = new CurrentPasswordVerifier(identityProviderDao, sessionTerminationDao);
    }

    @Test
    void shouldCheckThePasswordByAttemptingALogin() {
        when(identityProviderDao.authenticate(any())).thenReturn(TOKEN);

        verifier.verify("denny.afrizal", "K7mQ2x#9");

        verify(identityProviderDao).authenticate(new UserCredentials("denny.afrizal", "K7mQ2x#9"));
    }

    /**
     * The attempt mints a token pair nobody asked for. Leaving it alive would
     * add a session to the account on every password change, and its refresh
     * token would be a working credential created only to be discarded.
     */
    @Test
    void shouldEndTheSessionTheCheckOpened() {
        when(identityProviderDao.authenticate(any())).thenReturn(TOKEN);

        verifier.verify("denny.afrizal", "K7mQ2x#9");

        verify(sessionTerminationDao).revoke(new RefreshToken("refresh"));
    }

    @Test
    void shouldRethrowAWrongPasswordUnchanged() {
        InvalidCredentialsException wrong = new InvalidCredentialsException("Invalid username or password");
        when(identityProviderDao.authenticate(any())).thenThrow(wrong);

        assertSame(wrong, assertThrows(InvalidCredentialsException.class,
                () -> verifier.verify("denny.afrizal", "salah")));
        verifyNoInteractions(sessionTerminationDao);
    }

    /**
     * The check goes through brute force detection like any other login, which
     * is exactly what stops this endpoint from being a password oracle.
     */
    @Test
    void shouldSurfaceALockedAccount() {
        when(identityProviderDao.authenticate(any())).thenThrow(new AccountLockedException("locked"));

        assertThrows(AccountLockedException.class, () -> verifier.verify("denny.afrizal", "salah"));
    }

    @Test
    void shouldPropagateIdentityProviderOutages() {
        when(identityProviderDao.authenticate(any())).thenThrow(new IdentityProviderUnavailableException("down"));

        assertThrows(IdentityProviderUnavailableException.class,
                () -> verifier.verify("denny.afrizal", "K7mQ2x#9"));
    }

    /**
     * The password was correct; failing to tidy up afterwards is our problem,
     * not the caller's, and the stray session expires on its own.
     */
    @Test
    void shouldSucceedEvenWhenTheSessionCannotBeEnded() {
        when(identityProviderDao.authenticate(any())).thenReturn(TOKEN);
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(sessionTerminationDao).revoke(any());

        assertDoesNotThrow(() -> verifier.verify("denny.afrizal", "K7mQ2x#9"));
    }

    @Test
    void shouldTreatAnAlreadyEndedSessionAsTidiedUp() {
        when(identityProviderDao.authenticate(any())).thenReturn(TOKEN);
        doThrow(new InvalidRefreshTokenException("expired")).when(sessionTerminationDao).revoke(any());

        assertDoesNotThrow(() -> verifier.verify("denny.afrizal", "K7mQ2x#9"));
    }

    @Test
    void shouldNotTryToEndASessionWhenNoRefreshTokenCameBack() {
        when(identityProviderDao.authenticate(any()))
                .thenReturn(new AuthToken("access", null, "Bearer", 300, 0, "profile"));

        verifier.verify("denny.afrizal", "K7mQ2x#9");

        verify(sessionTerminationDao, never()).revoke(any());
    }

    @Test
    void shouldNotTryToEndASessionWhenTheRefreshTokenIsBlank() {
        when(identityProviderDao.authenticate(any()))
                .thenReturn(new AuthToken("access", "  ", "Bearer", 300, 0, "profile"));

        verifier.verify("denny.afrizal", "K7mQ2x#9");

        verify(sessionTerminationDao, never()).revoke(any());
    }

    @Test
    void shouldNotTryToEndASessionWhenNoTokenCameBackAtAll() {
        when(identityProviderDao.authenticate(any())).thenReturn(null);

        verifier.verify("denny.afrizal", "K7mQ2x#9");

        verify(sessionTerminationDao, never()).revoke(any());
    }
}
