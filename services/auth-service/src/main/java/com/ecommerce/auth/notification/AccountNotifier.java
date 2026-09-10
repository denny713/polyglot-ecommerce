package com.ecommerce.auth.notification;

import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.RawPassword;

/**
 * Contract for telling an account holder something out of band.
 *
 * <p>
 * An interface rather than a direct call to the mailer, for the same reason
 * {@code IdentityProviderDao} is one: the account service should depend on "the
 * holder was told", not on SMTP. Swapping email for an SMS gateway, or adding a
 * second channel, then means another implementation and no change here or
 * upstream (Dependency Inversion Principle).
 *
 * @see com.ecommerce.auth.notification.mail.MailAccountNotifier
 */
public interface AccountNotifier {

    /**
     * Sends the account holder the password generated for them at registration.
     *
     * <p>
     * This is the only time the value is ever transmitted: it is not returned by
     * the API, not stored by this service, and not recoverable afterwards. A
     * failure here therefore means the account is unusable, which is why the
     * caller is expected to undo the registration rather than carry on.
     *
     * @param account  the account that was created
     * @param password the password it was given
     * @throws NotificationDeliveryException the message could not be handed over
     */
    void sendTemporaryPassword(Account account, RawPassword password);
}
