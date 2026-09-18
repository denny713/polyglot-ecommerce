package com.ecommerce.auth.service;

import com.ecommerce.auth.dao.AccountProviderDao;
import com.ecommerce.auth.enums.AuthErrorCode;
import com.ecommerce.auth.enums.AccountErrorCode;
import com.ecommerce.auth.exception.AccountAlreadyExistsException;
import com.ecommerce.auth.exception.AccountLockedException;
import com.ecommerce.auth.exception.AccountNotFoundException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.InvalidAccountDataException;
import com.ecommerce.auth.exception.InvalidCredentialsException;
import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountRole;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.PasswordChange;
import com.ecommerce.auth.model.RawPassword;
import com.ecommerce.auth.notification.AccountNotifier;
import com.ecommerce.auth.security.CurrentPasswordVerifier;
import com.ecommerce.auth.security.TemporaryPasswordGenerator;
import com.ecommerce.auth.service.impl.AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Unit tests for the account business layer. */
class AccountServiceImplTest {

    private static final String ACCOUNT_ID = "8f1a5c2e-6b3d-4f7a-9e21-0c4d8b5a7f36";

    private static final NewAccount NEW_ACCOUNT =
            new NewAccount("denny.afrizal", "denny@mail.com", "Denny", "Afrizal");

    private static final Account CREATED =
            new Account(ACCOUNT_ID, "denny.afrizal", "denny@mail.com", "Denny", "Afrizal", true);

    private AccountProviderDao accountProviderDao;
    private AccountNotifier accountNotifier;
    private CurrentPasswordVerifier currentPasswordVerifier;
    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        accountProviderDao = mock(AccountProviderDao.class);
        accountNotifier = mock(AccountNotifier.class);
        currentPasswordVerifier = mock(CurrentPasswordVerifier.class);

        // The real generator, not a mock: it has no collaborators, it is fast,
        // and a stubbed one would hide the fact that register must work with
        // whatever it produces.
        service = new AccountServiceImpl(accountProviderDao, new TemporaryPasswordGenerator(),
                accountNotifier, currentPasswordVerifier);
    }

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Test
    void shouldReturnTheAccountTheProviderCreated() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        assertSame(CREATED, service.doRegister(NEW_ACCOUNT), "the service must not rebuild the account");
    }

    @Test
    void shouldGiveTheNewAccountAGeneratedPassword() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        service.doRegister(NEW_ACCOUNT);

        ArgumentCaptor<RawPassword> captor = ArgumentCaptor.forClass(RawPassword.class);
        verify(accountProviderDao).doCreate(eq(NEW_ACCOUNT), captor.capture());
        assertEquals(8, captor.getValue().value().length());
    }

    /**
     * The email is the only place the password ever appears — it is not
     * returned by the API and not stored here — so the same value that was set
     * on the account has to be the one that goes out.
     */
    @Test
    void shouldMailTheSamePasswordItSetOnTheAccount() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        service.doRegister(NEW_ACCOUNT);

        ArgumentCaptor<RawPassword> stored = ArgumentCaptor.forClass(RawPassword.class);
        ArgumentCaptor<RawPassword> mailed = ArgumentCaptor.forClass(RawPassword.class);
        verify(accountProviderDao).doCreate(eq(NEW_ACCOUNT), stored.capture());
        verify(accountNotifier).sendTemporaryPassword(eq(CREATED), mailed.capture());

        assertEquals(stored.getValue().value(), mailed.getValue().value());
    }

    @Test
    void shouldGenerateADifferentPasswordEachTime() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        service.doRegister(NEW_ACCOUNT);
        service.doRegister(NEW_ACCOUNT);

        ArgumentCaptor<RawPassword> captor = ArgumentCaptor.forClass(RawPassword.class);
        verify(accountProviderDao, times(2)).doCreate(eq(NEW_ACCOUNT), captor.capture());
        assertNotEquals(captor.getAllValues().get(0).value(), captor.getAllValues().get(1).value());
    }

    /**
     * The account would otherwise sit there with a password nobody knows,
     * holding the username and email address against the retry.
     */
    @Test
    void shouldRemoveTheAccountAgainWhenItsPasswordEmailCannotBeSent() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);
        doThrow(new NotificationDeliveryException("smtp down", new RuntimeException()))
                .when(accountNotifier).sendTemporaryPassword(any(), any());

        assertThrows(NotificationDeliveryException.class, () -> service.doRegister(NEW_ACCOUNT));

        verify(accountProviderDao).doDelete(ACCOUNT_ID);
    }

    /**
     * If the undo fails too there is nothing further this service can do, so it
     * still reports the failure the caller can act on rather than a second one
     * about cleanup.
     */
    @Test
    void shouldStillReportTheDeliveryFailureWhenTheUndoAlsoFails() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);
        NotificationDeliveryException delivery =
                new NotificationDeliveryException("smtp down", new RuntimeException());
        doThrow(delivery).when(accountNotifier).sendTemporaryPassword(any(), any());
        doThrow(new IdentityProviderUnavailableException("down")).when(accountProviderDao).doDelete(any());

        assertSame(delivery, assertThrows(NotificationDeliveryException.class,
                () -> service.doRegister(NEW_ACCOUNT)));
    }

    @Test
    void shouldRethrowATakenUsernameUnchanged() {
        AccountAlreadyExistsException taken = new AccountAlreadyExistsException("taken");
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenThrow(taken);

        // Same instance: logging a rejected registration must not repackage it.
        assertSame(taken, assertThrows(AccountAlreadyExistsException.class, () -> service.doRegister(NEW_ACCOUNT)));
    }

    @Test
    void shouldNotSendAnEmailWhenTheAccountWasNeverCreated() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any()))
                .thenThrow(new AccountAlreadyExistsException("taken"));

        assertThrows(AccountAlreadyExistsException.class, () -> service.doRegister(NEW_ACCOUNT));

        verifyNoInteractions(accountNotifier);
    }

    @Test
    void shouldPropagateIdentityProviderOutagesOnRegister() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any()))
                .thenThrow(new IdentityProviderUnavailableException("down"));

        assertThrows(IdentityProviderUnavailableException.class, () -> service.doRegister(NEW_ACCOUNT));
    }

    @Test
    void shouldRejectANullAccountBeforeCallingTheProvider() {
        assertEquals("newAccount must not be null",
                assertThrows(NullPointerException.class, () -> service.doRegister(null)).getMessage());
        verifyNoInteractions(accountProviderDao);
        verifyNoInteractions(accountNotifier);
    }

    // ------------------------------------------------------------------
    // register — the role
    // ------------------------------------------------------------------

    /** This endpoint is self-service registration, so {@code user} is the only role it may ever produce. */
    @Test
    void shouldGiveEveryRegisteredAccountTheUserRole() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        service.doRegister(NEW_ACCOUNT);

        verify(accountProviderDao).doAssignRole(ACCOUNT_ID, AccountRole.USER);
    }

    /**
     * Captured rather than merely verified, so that a second grant sneaked in
     * later — an administrative one above all — fails this test rather than
     * passing it silently alongside the expected one.
     */
    @Test
    void shouldGrantThatOneRoleAndNothingElse() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        service.doRegister(NEW_ACCOUNT);

        ArgumentCaptor<AccountRole> captor = ArgumentCaptor.forClass(AccountRole.class);
        verify(accountProviderDao, times(1)).doAssignRole(eq(ACCOUNT_ID), captor.capture());

        assertEquals(List.of(AccountRole.USER), captor.getAllValues(),
                "registration must never hand out ADMIN");
    }

    /**
     * The role is granted to the id the provider assigned, not to the username:
     * that id is the {@code sub} of every token later issued for the account.
     */
    @Test
    void shouldGrantTheRoleToTheIdTheProviderAssigned() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        service.doRegister(NEW_ACCOUNT);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(accountProviderDao).doAssignRole(captor.capture(), eq(AccountRole.USER));
        assertEquals(CREATED.id(), captor.getValue());
    }

    /**
     * Ordered on purpose: the email carries the password, and a message about an
     * account that is about to be deleted again is one that cannot be recalled.
     */
    @Test
    void shouldGrantTheRoleBeforeMailingThePassword() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);

        service.doRegister(NEW_ACCOUNT);

        InOrder order = inOrder(accountProviderDao, accountNotifier);
        order.verify(accountProviderDao).doCreate(eq(NEW_ACCOUNT), any());
        order.verify(accountProviderDao).doAssignRole(ACCOUNT_ID, AccountRole.USER);
        order.verify(accountNotifier).sendTemporaryPassword(eq(CREATED), any());
    }

    /**
     * An account with no role logs in successfully and is then refused by every
     * endpoint that asks for {@code user} — which reads as a broken platform
     * rather than as a failed registration.
     */
    @Test
    void shouldRemoveTheAccountAgainWhenTheRoleCannotBeGranted() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(accountProviderDao).doAssignRole(any(), any());

        assertThrows(IdentityProviderUnavailableException.class, () -> service.doRegister(NEW_ACCOUNT));

        verify(accountProviderDao).doDelete(ACCOUNT_ID);
    }

    @Test
    void shouldNotMailAPasswordForAnAccountItIsAboutToRemove() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(accountProviderDao).doAssignRole(any(), any());

        assertThrows(IdentityProviderUnavailableException.class, () -> service.doRegister(NEW_ACCOUNT));

        verifyNoInteractions(accountNotifier);
    }

    @Test
    void shouldReportTheRoleFailureUnchanged() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);
        AccountNotFoundException gone = new AccountNotFoundException("the account vanished mid-registration");
        doThrow(gone).when(accountProviderDao).doAssignRole(any(), any());

        assertSame(gone, assertThrows(AccountNotFoundException.class, () -> service.doRegister(NEW_ACCOUNT)));
    }

    @Test
    void shouldStillReportTheRoleFailureWhenTheUndoAlsoFails() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any())).thenReturn(CREATED);
        IdentityProviderUnavailableException roleFailure = new IdentityProviderUnavailableException("down");
        doThrow(roleFailure).when(accountProviderDao).doAssignRole(any(), any());
        doThrow(new IdentityProviderUnavailableException("still down"))
                .when(accountProviderDao).doDelete(any());

        assertSame(roleFailure, assertThrows(IdentityProviderUnavailableException.class,
                () -> service.doRegister(NEW_ACCOUNT)));
    }

    @Test
    void shouldNotGrantARoleWhenTheAccountWasNeverCreated() {
        when(accountProviderDao.doCreate(eq(NEW_ACCOUNT), any()))
                .thenThrow(new AccountAlreadyExistsException("taken"));

        assertThrows(AccountAlreadyExistsException.class, () -> service.doRegister(NEW_ACCOUNT));

        verify(accountProviderDao, never()).doAssignRole(any(), any());
    }

    // ------------------------------------------------------------------
    // findById
    // ------------------------------------------------------------------

    @Test
    void shouldReturnTheAccountTheProviderHolds() {
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenReturn(CREATED);

        assertSame(CREATED, service.doFindById(ACCOUNT_ID), "the service must not rebuild the account");
    }

    @Test
    void shouldRethrowAMissingAccountOnLookup() {
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenThrow(missing);

        assertSame(missing, assertThrows(AccountNotFoundException.class, () -> service.doFindById(ACCOUNT_ID)));
    }

    @Test
    void shouldPropagateIdentityProviderOutagesOnLookup() {
        when(accountProviderDao.doFindById(ACCOUNT_ID))
                .thenThrow(new IdentityProviderUnavailableException("down"));

        assertThrows(IdentityProviderUnavailableException.class, () -> service.doFindById(ACCOUNT_ID));
    }

    @Test
    void shouldRejectANullAccountIdOnLookup() {
        assertEquals("accountId must not be null",
                assertThrows(NullPointerException.class, () -> service.doFindById(null)).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    @Test
    void shouldRejectABlankAccountIdOnLookup() {
        assertEquals("accountId must not be blank",
                assertThrows(IllegalArgumentException.class, () -> service.doFindById("  ")).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    // ------------------------------------------------------------------
    // update
    // ------------------------------------------------------------------

    @Test
    void shouldPassAnUpdateStraightThroughToTheProvider() {
        AccountUpdate update = new AccountUpdate("baru@mail.com", null, null);

        service.doUpdate(ACCOUNT_ID, update);

        verify(accountProviderDao).doUpdate(ACCOUNT_ID, update);
    }

    /**
     * The rule that cannot live on a field annotation: it is the combination
     * "every field absent" that is meaningless, and sending it on would spend two
     * round trips on a no-op.
     */
    @Test
    void shouldRefuseAnUpdateWithNothingToChange() {
        InvalidAccountDataException thrown = assertThrows(InvalidAccountDataException.class,
                () -> service.doUpdate(ACCOUNT_ID, new AccountUpdate(null, null, null)));

        assertEquals("At least one field must be provided to update", thrown.getMessage());
        assertEquals(AccountErrorCode.INVALID_ACCOUNT_DATA, thrown.errorCode());
        verify(accountProviderDao, never()).doUpdate(any(), any());
    }

    @Test
    void shouldRethrowAMissingAccountOnUpdate() {
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        doThrow(missing).when(accountProviderDao).doUpdate(eq(ACCOUNT_ID), any());

        assertSame(missing, assertThrows(AccountNotFoundException.class,
                () -> service.doUpdate(ACCOUNT_ID, new AccountUpdate("baru@mail.com", null, null))));
    }

    @Test
    void shouldPropagateIdentityProviderOutagesOnUpdate() {
        doThrow(new IdentityProviderUnavailableException("down"))
                .when(accountProviderDao).doUpdate(eq(ACCOUNT_ID), any());

        assertThrows(IdentityProviderUnavailableException.class,
                () -> service.doUpdate(ACCOUNT_ID, new AccountUpdate("baru@mail.com", null, null)));
    }

    @Test
    void shouldRejectANullAccountIdOnUpdate() {
        assertEquals("accountId must not be null", assertThrows(NullPointerException.class,
                () -> service.doUpdate(null, new AccountUpdate("baru@mail.com", null, null))).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    @Test
    void shouldRejectABlankAccountIdOnUpdate() {
        assertEquals("accountId must not be blank", assertThrows(IllegalArgumentException.class,
                () -> service.doUpdate("  ", new AccountUpdate("baru@mail.com", null, null))).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    @Test
    void shouldRejectANullUpdate() {
        assertEquals("update must not be null", assertThrows(NullPointerException.class,
                () -> service.doUpdate(ACCOUNT_ID, null)).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    // ------------------------------------------------------------------
    // changePassword
    // ------------------------------------------------------------------

    @Test
    void shouldStoreTheNewPasswordOnceTheOldOneChecksOut() {
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenReturn(CREATED);

        service.doChangePassword(ACCOUNT_ID, new PasswordChange("K7mQ2x#9", "Rahasia#2026"));

        verify(currentPasswordVerifier).verify("denny.afrizal", "K7mQ2x#9");
        verify(accountProviderDao).doChangePassword(ACCOUNT_ID, new RawPassword("Rahasia#2026"));
    }

    /**
     * The id is what the API addresses, the username is what a login attempt
     * needs, and only the provider knows the mapping between them.
     */
    @Test
    void shouldResolveTheUsernameFromTheAccountIdBeforeVerifying() {
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenReturn(
                new Account(ACCOUNT_ID, "someone.else", "x@mail.com", "X", "Y", true));

        service.doChangePassword(ACCOUNT_ID, new PasswordChange("K7mQ2x#9", "Rahasia#2026"));

        verify(currentPasswordVerifier).verify("someone.else", "K7mQ2x#9");
    }

    /**
     * The whole point of asking for the old password: a stolen token alone must
     * not be enough to take the account over permanently.
     */
    @Test
    void shouldNotStoreANewPasswordWhenTheOldOneIsWrong() {
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenReturn(CREATED);
        InvalidCredentialsException wrong = new InvalidCredentialsException("Invalid username or password");
        doThrow(wrong).when(currentPasswordVerifier).verify(any(), any());

        assertSame(wrong, assertThrows(InvalidCredentialsException.class,
                () -> service.doChangePassword(ACCOUNT_ID, new PasswordChange("salah", "Rahasia#2026"))));

        verify(accountProviderDao, never()).doChangePassword(any(), any());
    }

    @Test
    void shouldSurfaceALockedAccountFromTheVerification() {
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenReturn(CREATED);
        doThrow(new AccountLockedException("locked")).when(currentPasswordVerifier).verify(any(), any());

        AccountLockedException thrown = assertThrows(AccountLockedException.class,
                () -> service.doChangePassword(ACCOUNT_ID, new PasswordChange("salah", "Rahasia#2026")));

        assertEquals(AuthErrorCode.ACCOUNT_LOCKED, thrown.errorCode());
    }

    /**
     * Re-setting the same value would read as a successful rotation in an audit
     * log while changing nothing — refused before the provider is touched.
     */
    @Test
    void shouldRefuseANewPasswordIdenticalToTheOldOne() {
        InvalidAccountDataException thrown = assertThrows(InvalidAccountDataException.class,
                () -> service.doChangePassword(ACCOUNT_ID, new PasswordChange("K7mQ2x#9", "K7mQ2x#9")));

        assertEquals("The new password must be different from the current one", thrown.getMessage());
        verifyNoInteractions(accountProviderDao);
        verifyNoInteractions(currentPasswordVerifier);
    }

    @Test
    void shouldRethrowAMissingAccountOnPasswordChange() {
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenThrow(missing);

        assertSame(missing, assertThrows(AccountNotFoundException.class,
                () -> service.doChangePassword(ACCOUNT_ID, new PasswordChange("K7mQ2x#9", "Rahasia#2026"))));
        verifyNoInteractions(currentPasswordVerifier);
    }

    @Test
    void shouldRethrowAPasswordTheRealmPolicyRefused() {
        when(accountProviderDao.doFindById(ACCOUNT_ID)).thenReturn(CREATED);
        InvalidAccountDataException refused = new InvalidAccountDataException("Password policy not met");
        doThrow(refused).when(accountProviderDao).doChangePassword(any(), any());

        assertSame(refused, assertThrows(InvalidAccountDataException.class,
                () -> service.doChangePassword(ACCOUNT_ID, new PasswordChange("K7mQ2x#9", "lemah"))));
    }

    @Test
    void shouldRejectANullAccountIdOnPasswordChange() {
        assertEquals("accountId must not be null", assertThrows(NullPointerException.class,
                () -> service.doChangePassword(null, new PasswordChange("K7mQ2x#9", "Rahasia#2026"))).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    @Test
    void shouldRejectABlankAccountIdOnPasswordChange() {
        assertEquals("accountId must not be blank", assertThrows(IllegalArgumentException.class,
                () -> service.doChangePassword(" ", new PasswordChange("K7mQ2x#9", "Rahasia#2026"))).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    @Test
    void shouldRejectANullChange() {
        assertEquals("change must not be null", assertThrows(NullPointerException.class,
                () -> service.doChangePassword(ACCOUNT_ID, null)).getMessage());
        verifyNoInteractions(accountProviderDao);
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    @Test
    void shouldPassADeleteStraightThroughToTheProvider() {
        service.doDelete(ACCOUNT_ID);

        verify(accountProviderDao).doDelete(ACCOUNT_ID);
    }

    /**
     * Unlike logout, delete is not silently idempotent — a 404 here is
     * information the caller can act on, and it leaks nothing, since it had to
     * hold the id to get this far.
     */
    @Test
    void shouldRethrowAMissingAccountOnDelete() {
        AccountNotFoundException missing = new AccountNotFoundException("gone");
        doThrow(missing).when(accountProviderDao).doDelete(ACCOUNT_ID);

        assertSame(missing, assertThrows(AccountNotFoundException.class, () -> service.doDelete(ACCOUNT_ID)));
    }

    @Test
    void shouldPropagateIdentityProviderOutagesOnDelete() {
        doThrow(new IdentityProviderUnavailableException("down")).when(accountProviderDao).doDelete(ACCOUNT_ID);

        assertThrows(IdentityProviderUnavailableException.class, () -> service.doDelete(ACCOUNT_ID));
    }

    @Test
    void shouldRejectANullAccountIdOnDelete() {
        assertThrows(NullPointerException.class, () -> service.doDelete(null));
        verifyNoInteractions(accountProviderDao);
    }

    @Test
    void shouldRejectABlankAccountIdOnDelete() {
        assertEquals("accountId must not be blank",
                assertThrows(IllegalArgumentException.class, () -> service.doDelete("")).getMessage());
        verifyNoInteractions(accountProviderDao);
    }
}
