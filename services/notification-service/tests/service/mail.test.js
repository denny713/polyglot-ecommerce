jest.mock('../../src/configuration/mailer', () => ({ sendMail: jest.fn() }));

const transporter = require('../../src/configuration/mailer');
const { sendEmail } = require('../../src/service/mail');

describe('sendEmail', () => {
    const originalEnv = process.env;

    beforeEach(() => {
        process.env = { ...originalEnv };
        transporter.sendMail.mockResolvedValue({ messageId: '<abc@mail>' });
    });

    afterAll(() => {
        process.env = originalEnv;
    });

    const mail = { to: 'jane@example.com', subject: 'Hi', text: 'Body' };

    it('sends from MAIL_FROM and returns the message id', async () => {
        process.env.MAIL_FROM = 'noreply@ecommerce.test';
        process.env.SMTP_USER = 'smtp@ecommerce.test';

        await expect(sendEmail(mail)).resolves.toBe('<abc@mail>');
        expect(transporter.sendMail).toHaveBeenCalledWith({
            from: 'noreply@ecommerce.test',
            ...mail,
        });
    });

    it('falls back to SMTP_USER when MAIL_FROM is not set', async () => {
        delete process.env.MAIL_FROM;
        process.env.SMTP_USER = 'smtp@ecommerce.test';

        await sendEmail(mail);

        expect(transporter.sendMail.mock.calls[0][0].from).toBe('smtp@ecommerce.test');
    });

    it('propagates transport errors', async () => {
        transporter.sendMail.mockRejectedValue(new Error('SMTP down'));

        await expect(sendEmail(mail)).rejects.toThrow('SMTP down');
    });
});
