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
 * @see com.ecommerce.auth.service.impl.AccountServiceImpl
 */
public interface AccountService {

    /**
     * Registers a new account with the {@link AccountRole#USER} role, gives it a
     * generated password, and mails that password to the address on the profile.
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
     * @param accountId the provider's id for the account
     * @throws AccountException                    no such account
     * @throws IdentityProviderUnavailableException the provider is unavailable
     */
    void doDelete(String accountId);
}
