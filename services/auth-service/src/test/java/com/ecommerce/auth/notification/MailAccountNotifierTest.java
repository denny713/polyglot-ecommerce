package com.ecommerce.auth.notification;

import com.ecommerce.auth.exception.NotificationDeliveryException;
import com.ecommerce.auth.model.Account;
import com.ecommerce.auth.model.RawPassword;
import com.ecommerce.auth.notification.mail.MailAccountNotifier;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/** Unit tests for the registration email. */
class MailAccountNotifierTest {

    private static final Account ACCOUNT = new Account(
            "8f1a5c2e", "denny.afrizal", "denny@mail.com", "Denny", "Afrizal", true);

    private static final RawPassword PASSWORD = new RawPassword("K7mQ2x#9");

    private Mailer mailer;
    private MailAccountNotifier notifier;

    @BeforeEach
    void setUp() {
        mailer = mock(Mailer.class);
        notifier = new MailAccountNotifier(mailer);
    }

    @Test
    void shouldSendToTheAddressOnTheAccount() {
        notifier.sendTemporaryPassword(ACCOUNT, PASSWORD);

        assertEquals(java.util.List.of("denny@mail.com"), captured().getTo());
    }

    @Test
    void shouldCarryTheGeneratedPasswordAndTheUsername() {
        notifier.sendTemporaryPassword(ACCOUNT, PASSWORD);

        String body = captured().getText();
        assertTrue(body.contains("K7mQ2x#9"), "the password is missing from the only message that carries it");
        assertTrue(body.contains("denny.afrizal"), "the reader needs the username to sign in");
    }

    /**
     * Plain text on purpose: an HTML client is free to reflow, auto-link or
     * capitalize, and this is a value the reader has to retype exactly.
     */
    @Test
    void shouldSendPlainTextRatherThanHtml() {
        notifier.sendTemporaryPassword(ACCOUNT, PASSWORD);

        Mail mail = captured();
        assertFalse(mail.getText() == null || mail.getText().isBlank());
        assertTrue(mail.getHtml() == null || mail.getHtml().isBlank(), "an HTML body may mangle the password");
    }

    @Test
    void shouldGiveTheMessageASubject() {
        notifier.sendTemporaryPassword(ACCOUNT, PASSWORD);

        assertEquals("Your polygot-ecommerce account", captured().getSubject());
    }

    /**
     * The caller has to know, because it is holding the only other copy of the
     * password and has to undo the registration.
     */
    @Test
    void shouldReportADeliveryFailureRatherThanSwallowIt() {
        IllegalStateException refused = new IllegalStateException("connection refused");
        doThrow(refused).when(mailer).send(any(Mail.class));

        NotificationDeliveryException thrown = assertThrows(NotificationDeliveryException.class,
                () -> notifier.sendTemporaryPassword(ACCOUNT, PASSWORD));

        assertEquals("Could not send the email carrying the generated password", thrown.getMessage());
        assertSame(refused, thrown.getCause(), "the real fault must stay in the log");
    }

    @Test
    void shouldReportAnyMailerFailureTheSameWay() {
        doThrow(new RuntimeException("mailbox unavailable")).when(mailer).send(any(Mail.class));

        assertThrows(NotificationDeliveryException.class,
                () -> notifier.sendTemporaryPassword(ACCOUNT, PASSWORD));
    }

    private Mail captured() {
        ArgumentCaptor<Mail> captor = ArgumentCaptor.forClass(Mail.class);
        verify(mailer).send(captor.capture());
        return captor.getValue();
    }
}
