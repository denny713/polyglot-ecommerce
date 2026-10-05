// Email templates for events published by auth-service, keyed by eventType.
// Plain text on purpose: an HTML client is free to reflow, auto-link or
// capitalize, and the registration email carries a password the reader has to
// retype exactly.

const SIGNATURE = `
Regards,
Ecommerce Team`;

const FIELD_LABELS = {
    email: 'email address',
    firstName: 'first name',
    lastName: 'last name',
};

const accountRegistered = (event) => ({
    subject: 'Your ecommerce account',
    text: `Hello ${event.firstName},

An account has been created for you on ecommerce.

    Username:  ${event.username}
    Password:  ${event.data.temporaryPassword}

Sign in with these, then change the password straight away — this one was generated for you and was sent over email, so treat it as known to anyone who can read this message.

If you did not expect this email, you can ignore it: the account cannot be used until someone signs in with the password above.
${SIGNATURE}`,
});

const accountUpdated = (event) => {
    const fields = (event.data.changedFields || []).map((field) => FIELD_LABELS[field] || field);

    return {
        subject: 'Your ecommerce account was updated',
        text: `Hello ${event.firstName},

The following details of your account (${event.username}) were changed on ${event.occurredAt}:

${fields.map((field) => `    - ${field}`).join('\n')}

If you did not make this change, sign in and change your password straight away.
${SIGNATURE}`,
    };
};

const passwordChanged = (event) => ({
    subject: 'Your ecommerce password was changed',
    text: `Hello ${event.firstName},

The password of your account (${event.username}) was changed on ${event.occurredAt}.

If you did not make this change, contact us straight away — someone else may have access to your account.
${SIGNATURE}`,
});

const accountDeleted = (event) => ({
    subject: 'Your ecommerce account was deleted',
    text: `Hello ${event.firstName},

Your account (${event.username}) was deleted on ${event.occurredAt}. You can no longer sign in with it.

If you did not ask for this, contact us.
${SIGNATURE}`,
});

module.exports = {
    ACCOUNT_REGISTERED: accountRegistered,
    ACCOUNT_UPDATED: accountUpdated,
    PASSWORD_CHANGED: passwordChanged,
    ACCOUNT_DELETED: accountDeleted,
};
