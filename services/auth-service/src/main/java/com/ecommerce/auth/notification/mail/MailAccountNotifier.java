package com.ecommerce.auth.notification.mail;

import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.RawPassword;
import com.ecommerce.auth.notification.AccountNotifier;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Sends the registration email over SMTP.
 *
 * <p>
 * This is the only class in the service that knows the notification channel is
 * email, in the same way {@code KeycloakIdentityProviderDao} is the only one
 * that knows the identity provider is Keycloak.
 *
 * <p>
 * The message is plain text on purpose. An HTML mail client is free to reflow,
 * auto-link or "helpfully" capitalize a password; plain text between blank lines
 * arrives as it was written, which for a value the reader has to type by hand
 * matters more than looking nice.
 */
@ApplicationScoped
public class MailAccountNotifier implements AccountNotifier {

    private static final Logger LOG = Logger.getLogger(MailAccountNotifier.class);

    private static final String SUBJECT = "Your polygot-ecommerce account";

    private final Mailer mailer;

    @Inject
    public MailAccountNotifier(Mailer mailer) {
        this.mailer = mailer;
    }

    @Override
    public void sendTemporaryPassword(Account account, RawPassword password) {
        try {
            mailer.send(Mail.withText(account.email(), SUBJECT, body(account, password)));
        } catch (RuntimeException e) {
            // Deliberately broad: the mailer wraps connection refusals, timeouts,
            // authentication failures and rejected recipients in a handful of
            // unrelated types, and the caller's response to all of them is the
            // same. The cause is kept so the log still names the real fault.
            LOG.errorf(e, "Could not send the registration email for account %s", account.id());
            throw new NotificationDeliveryException(
                    "Could not send the email carrying the generated password", e);
        }

        // The password itself is never logged — that is the whole point of
        // RawPassword#toString. Only the fact of the send is recorded.
        LOG.infof("Sent the generated password for account %s", account.id());
    }

    /**
     * The address is not repeated in the body and the username is: a mail client
     * already shows the recipient, and what the reader actually needs next is the
     * pair they will type into the login form.
     */
    private String body(Account account, RawPassword password) {
        return """
                Hello %s,

                An account has been created for you on polygot-ecommerce.

                    Username:  %s
                    Password:  %s

                Sign in with these, then change the password straight away — this one \
                was generated for you and was sent over email, so treat it as known to \
                anyone who can read this message.

                If you did not expect this email, you can ignore it: the account cannot \
                be used until someone signs in with the password above.
                """.formatted(account.firstName(), account.username(), password.value());
    }
}
