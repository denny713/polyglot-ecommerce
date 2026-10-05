const templates = require('../template/account');
const { sendEmail } = require('../service/mail');
const { UnprocessableEventError } = require('./error');

const handleAccountEvent = async (event) => {
    const template = templates[event.eventType];
    if (!template) {
        throw new UnprocessableEventError(`Unknown account event type: ${event.eventType}`);
    }

    if (!event.email) {
        throw new UnprocessableEventError(`Account event ${event.eventId} has no recipient`);
    }

    const { subject, text } = template({ ...event, data: event.data || {} });
    const messageId = await sendEmail({ to: event.email, subject, text });

    // Never log the event itself: the registration one carries a password.
    console.log(`Sent ${event.eventType} email for account ${event.accountId} (${messageId})`);
};

module.exports = { handleAccountEvent, UnprocessableEventError };
