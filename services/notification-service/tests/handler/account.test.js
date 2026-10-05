jest.mock('../../src/service/mail', () => ({ sendEmail: jest.fn() }));

const { sendEmail } = require('../../src/service/mail');
const { handleAccountEvent, UnprocessableEventError } = require('../../src/handler/account');

describe('handleAccountEvent', () => {
    let logSpy;

    beforeEach(() => {
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        sendEmail.mockResolvedValue('<message-id>');
    });

    afterEach(() => logSpy.mockRestore());

    const event = {
        eventId: 'evt-1',
        eventType: 'ACCOUNT_REGISTERED',
        accountId: 42,
        email: 'jane@example.com',
        firstName: 'Jane',
        username: 'jane.doe',
        data: { temporaryPassword: 'S3cr3t!' },
    };

    it('renders the template and emails the recipient', async () => {
        await handleAccountEvent(event);

        expect(sendEmail).toHaveBeenCalledTimes(1);
        const mail = sendEmail.mock.calls[0][0];
        expect(mail.to).toBe('jane@example.com');
        expect(mail.subject).toBe('Your ecommerce account');
        expect(mail.text).toContain('Password:  S3cr3t!');
        expect(logSpy).toHaveBeenCalledWith('Sent ACCOUNT_REGISTERED email for account 42 (<message-id>)');
    });

    it('never logs the password', async () => {
        await handleAccountEvent(event);

        const logged = logSpy.mock.calls.flat().join(' ');
        expect(logged).not.toContain('S3cr3t!');
    });

    it('defaults data to an empty object', async () => {
        await handleAccountEvent({ ...event, eventType: 'ACCOUNT_UPDATED', data: undefined });

        expect(sendEmail.mock.calls[0][0].subject).toBe('Your ecommerce account was updated');
    });

    it('rejects an unknown event type', async () => {
        const promise = handleAccountEvent({ ...event, eventType: 'SOMETHING_ELSE' });

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Unknown account event type: SOMETHING_ELSE');
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('rejects an event without a recipient', async () => {
        const promise = handleAccountEvent({ ...event, email: undefined });

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Account event evt-1 has no recipient');
        expect(sendEmail).not.toHaveBeenCalled();
    });

    it('propagates mail delivery failures', async () => {
        sendEmail.mockRejectedValue(new Error('SMTP down'));

        await expect(handleAccountEvent(event)).rejects.toThrow('SMTP down');
        expect(logSpy).not.toHaveBeenCalled();
    });
});
