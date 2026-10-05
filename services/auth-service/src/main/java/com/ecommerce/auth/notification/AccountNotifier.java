package com.ecommerce.auth.notification;

import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.AccountUpdate;
import com.ecommerce.auth.model.RawPassword;

/**
 * Contract for telling an account holder something out of band.
 * @see com.ecommerce.auth.notification.broker.BrokerAccountNotifier
 */
public interface AccountNotifier {

    /**
     * Sends the account holder the password generated for them at registration,
     * and returns only once the message is safely handed over.
     * @param account  the account that was created
     * @param password the password it was given
     * @throws NotificationDeliveryException the message could not be handed over
     */
    void sendTemporaryPassword(Account account, RawPassword password);

    /**
     * Tells the account holder their profile was changed.
     * @param account the account as it is after the change
     * @param update  the fields the change named
     * @throws NotificationDeliveryException the message could not be handed over
     */
    void notifyAccountUpdated(Account account, AccountUpdate update);

    /**
     * Tells the account holder their password was changed.
     * @param account the account whose password was replaced
     * @throws NotificationDeliveryException the message could not be handed over
     */
    void notifyPasswordChanged(Account account);

    /**
     * Tells the former account holder their account is gone.
     * @param account the account as it was just before it was deleted
     * @throws NotificationDeliveryException the message could not be handed over
     */
    void notifyAccountDeleted(Account account);
}
