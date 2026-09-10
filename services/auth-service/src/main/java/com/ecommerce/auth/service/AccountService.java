package com.ecommerce.auth.service;

import com.ecommerce.auth.exception.AccountException;
import com.ecommerce.auth.exception.AuthenticationException;
import com.ecommerce.auth.exception.IdentityProviderUnavailableException;
import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountRole;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.NewAccount;
import com.ecommerce.auth.model.PasswordChange;

/**
 * Contract for the account management business logic — this is what the
 * controller uses.
 *
 * <p>
 * Like {@link AuthenticationService}, it works with domain models
 * ({@link NewAccount}, {@link Account}) rather than HTTP DTOs, so the logic can
 * be reused from other triggers — an admin CLI, a bulk import job — without
 * dragging JAX-RS along.
 *
 * <p>
 * Every method takes the account id explicitly, even though the HTTP endpoints
 * all operate on "me". Resolving "me" needs a bearer token, and that is a
 * property of the transport, not of the business rule — keeping it in
 * {@code com.ecommerce.auth.security.CurrentAccount} leaves this interface
 * usable from callers that have no token at all, such as an admin CLI or an
 * import job. Knowing the <em>old password</em>, on the other hand, is a rule
 * about the account rather than about the caller, so that one does live here.
 *
 * @see com.ecommerce.auth.service.impl.AccountServiceImpl
 */
public interface AccountService {

    /**
     * Registers a new account with the {@link AccountRole#USER} role, gives it a
     * generated password, and mails that password to the address on the profile.
     *
     * <p>
     * The caller does not choose the password and never sees it. That keeps the
     * one endpoint open to the internet from being able to set a credential of
     * the caller's choosing on an address they do not control, and it means
     * possession of the mailbox is what proves the registration was genuine.
     *
     * <p>
     * Nor does the caller choose the role. Registration is self-service, so the
     * role is fixed at {@code user} — {@link NewAccount} deliberately has no
     * field for it, because a request that could name a role could ask for
     * {@link AccountRole#ADMIN}.
     *
     * <p>
     * The steps are treated as one: if the role cannot be granted or the email
     * cannot be sent, the account is removed again rather than left behind
     * half-registered — with a password nobody knows, or with no role and
     * therefore no access — while holding the username and email address against
     * the retry.
     *
     * @param newAccount the profile to create
     * @return the created account, including the id the provider assigned
     * @throws AccountException                    the username or email is taken,
     *                                             or the provider rejected the data
     * @throws NotificationDeliveryException       the email could not be sent; the
     *                                             account has been removed again
     * @throws IdentityProviderUnavailableException the provider is unavailable, or the
     *                                             role could not be granted; the
     *                                             account has been removed again
     */
    Account doRegister(NewAccount newAccount);

    /**
     * Reads an account.
     *
     * @param accountId the provider's id for the account
     * @return the account as the provider stored it
     * @throws AccountException                    no such account
     * @throws IdentityProviderUnavailableException the provider is unavailable
     */
    Account doFindById(String accountId);

    /**
     * Changes the profile fields named in {@code update}, leaving the rest alone.
     *
     * @param accountId the provider's id for the account
     * @param update    the fields to change; at least one must be present
     * @throws AccountException                    no such account, nothing to change,
     *                                             or the provider rejected the data
     * @throws IdentityProviderUnavailableException the provider is unavailable
     */
    void doUpdate(String accountId, AccountUpdate update);

    /**
     * Replaces the account's password, after checking that the caller knows the
     * current one.
     *
     * <p>
     * The check is a real login attempt against the identity provider, so it
     * counts towards the realm's brute force detection — this endpoint cannot be
     * turned into an offline password oracle, and enough wrong guesses lock the
     * account just as they would at the login endpoint.
     *
     * @param accountId the provider's id for the account
     * @param change    the current password and the one to replace it with
     * @throws AuthenticationException             the current password is wrong, or
     *                                             the account is locked or disabled
     * @throws AccountException                    no such account, the new password
     *                                             equals the old one, or the realm
     *                                             password policy refused it
     * @throws IdentityProviderUnavailableException the provider is unavailable
     */
    void doChangePassword(String accountId, PasswordChange change);

    /**
     * Deletes the account.
     *
     * <p>
     * Not idempotent, unlike {@link AuthenticationService#doLogout}: deleting an
     * account that is not there is a mistake worth reporting, and the caller had
     * to know its id to get this far, so a 404 tells it nothing it did not
     * already know.
     *
     * @param accountId the provider's id for the account
     * @throws AccountException                    no such account
     * @throws IdentityProviderUnavailableException the provider is unavailable
     */
    void doDelete(String accountId);
}
