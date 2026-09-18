package com.ecommerce.auth.notification;

import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.RawPassword;

/**
 * Contract for telling an account holder something out of band.
 * @see com.ecommerce.auth.notification.mail.MailAccountNotifier
 */
public interface AccountNotifier {

    /**
     * Sends the account holder the password generated for them at registration.
     * @param account  the account that was created
     * @param password the password it was given
     * @throws NotificationDeliveryException the message could not be handed over
     */
    void sendTemporaryPassword(Account account, RawPassword password);
}
