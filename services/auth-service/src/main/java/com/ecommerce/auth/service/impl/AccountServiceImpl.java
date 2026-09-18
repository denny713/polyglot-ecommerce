package com.ecommerce.auth.service.impl;

import com.ecommerce.auth.dao.AccountProviderDao;
import com.ecommerce.auth.exception.AccountException;
import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.InvalidAccountDataException;
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
import com.ecommerce.auth.service.AccountService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Objects;

/** Default implementation of {@link AccountService}. */
@ApplicationScoped
public class AccountServiceImpl implements AccountService {

    private static final Logger LOG = Logger.getLogger(AccountServiceImpl.class);

    private final AccountProviderDao accountProviderDao;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final AccountNotifier accountNotifier;
    private final CurrentPasswordVerifier currentPasswordVerifier;

    @Inject
    public AccountServiceImpl(AccountProviderDao accountProviderDao,
                              TemporaryPasswordGenerator passwordGenerator,
                              AccountNotifier accountNotifier,
                              CurrentPasswordVerifier currentPasswordVerifier) {
        this.accountProviderDao = accountProviderDao;
        this.passwordGenerator = passwordGenerator;
        this.accountNotifier = accountNotifier;
        this.currentPasswordVerifier = currentPasswordVerifier;
    }

    /** {@inheritDoc} */
    @Override
    public Account doRegister(NewAccount newAccount) {
        Objects.requireNonNull(newAccount, "newAccount must not be null");

        LOG.debugf("Registering account '%s'", newAccount.username());

        RawPassword password = passwordGenerator.generate();

        Account account;
        try {
            account = accountProviderDao.doCreate(newAccount, password);
        } catch (AccountException e) {
            // Logged here, not in the controller: a rejected registration is a
            // business event (audit trail material), not merely an HTTP error.
            LOG.warnf("Registration failed for '%s': %s", newAccount.username(), e.errorCode());
            throw e;
        }

        // Role before email, so a failure here costs nobody a message carrying
        // the password of an account that is about to be deleted again.
        assignRoleOrUndo(account, AccountRole.USER);
        deliverOrUndo(account, password);
        return account;
    }

    /** Grants the role, and removes the account again if that fails. */
    private void assignRoleOrUndo(Account account, AccountRole role) {
        try {
            accountProviderDao.doAssignRole(account.id(), role);
        } catch (RuntimeException e) {
            LOG.errorf("Could not grant role '%s' to account %s, removing it again",
                    role.roleName(), account.id());
            undo(account);
            throw e;
        }
    }

    /** Sends the generated password, and removes the account again if that fails. */
    private void deliverOrUndo(Account account, RawPassword password) {
        try {
            accountNotifier.sendTemporaryPassword(account, password);
        } catch (NotificationDeliveryException e) {
            LOG.errorf("Could not deliver the password for account %s, removing it again", account.id());
            undo(account);
            throw e;
        }
    }

    /** Removes a half-registered account, best effort. */
    private void undo(Account account) {
        try {
            accountProviderDao.doDelete(account.id());
        } catch (RuntimeException undoFailure) {
            LOG.errorf(undoFailure,
                    "Account %s ('%s') could not be removed after its registration failed halfway — "
                            + "it now exists with a password nobody knows and must be deleted by hand",
                    account.id(), account.username());
        }
    }

    @Override
    public Account doFindById(String accountId) {
        requireAccountId(accountId);

        try {
            return accountProviderDao.doFindById(accountId);
        } catch (AccountException e) {
            LOG.warnf("Lookup failed for account %s: %s", accountId, e.errorCode());
            throw e;
        }
    }

    @Override
    public void doUpdate(String accountId, AccountUpdate update) {
        requireAccountId(accountId);
        Objects.requireNonNull(update, "update must not be null");

        // Caught here rather than by a Bean Validation annotation because the rule
        // is about the request as a whole, not about any one field: it is the
        // combination "every field absent" that is meaningless. Sending it on
        // would spend two round trips on a no-op.
        if (update.isEmpty()) {
            throw new InvalidAccountDataException("At least one field must be provided to update");
        }

        try {
            accountProviderDao.doUpdate(accountId, update);
        } catch (AccountException e) {
            LOG.warnf("Update failed for account %s: %s", accountId, e.errorCode());
            throw e;
        }
    }

    @Override
    public void doChangePassword(String accountId, PasswordChange change) {
        requireAccountId(accountId);
        Objects.requireNonNull(change, "change must not be null");

        // Refused before the identity provider is touched: re-setting the same
        // value would look like a successful rotation in an audit log while
        // changing nothing, which is worse than an error.
        if (change.isNoOp()) {
            throw new InvalidAccountDataException("The new password must be different from the current one");
        }

        // Resolved first because verifying a password needs the username, while
        // everything else in this API addresses an account by id.
        Account account = accountProviderDao.doFindById(accountId);

        try {
            currentPasswordVerifier.verify(account.username(), change.oldPassword());
        } catch (AuthenticationException e) {
            // Audit trail material, exactly like a failed login — and for the
            // realm's brute force detection it is one.
            LOG.warnf("Password change refused for account %s: %s", accountId, e.errorCode());
            throw e;
        }

        try {
            accountProviderDao.doChangePassword(accountId, new RawPassword(change.newPassword()));
        } catch (AccountException e) {
            LOG.warnf("Password change failed for account %s: %s", accountId, e.errorCode());
            throw e;
        }

        LOG.infof("Password changed for account %s", accountId);
    }

    @Override
    public void doDelete(String accountId) {
        requireAccountId(accountId);

        try {
            accountProviderDao.doDelete(accountId);
        } catch (AccountException e) {
            LOG.warnf("Delete failed for account %s: %s", accountId, e.errorCode());
            throw e;
        }
    }

    private void requireAccountId(String accountId) {
        Objects.requireNonNull(accountId, "accountId must not be null");

        if (accountId.isBlank()) {
            throw new IllegalArgumentException("accountId must not be blank");
        }
    }
}
