jest.mock('nodemailer', () => ({ createTransport: jest.fn() }));

const nodemailer = require('nodemailer');

describe('mailer configuration', () => {
    const originalEnv = process.env;
    let transporter;
    let logSpy;
    let errorSpy;

    const loadMailer = () => {
        let mailer;
        jest.isolateModules(() => {
            mailer = require('../../src/configuration/mailer');
        });
        return mailer;
    };

    beforeEach(() => {
        process.env = { ...originalEnv, SMTP_HOST: 'smtp.example.com' };
        delete process.env.SMTP_PORT;
        delete process.env.SMTP_USER;
        delete process.env.SMTP_PASS;
        delete process.env.SMTP_REQUIRE_TLS;

        transporter = { verify: jest.fn() };
        nodemailer.createTransport.mockReturnValue(transporter);
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        logSpy.mockRestore();
        errorSpy.mockRestore();
    });

    afterAll(() => {
        process.env = originalEnv;
    });

    it('exports the created transporter', () => {
        expect(loadMailer()).toBe(transporter);
    });

    it('defaults to port 587 with STARTTLS and no auth', () => {
        loadMailer();

        expect(nodemailer.createTransport).toHaveBeenCalledWith({
            host: 'smtp.example.com',
            port: 587,
            secure: false,
            requireTLS: true,
            auth: undefined,
        });
    });

    it('uses implicit TLS on port 465', () => {
        process.env.SMTP_PORT = '465';

        loadMailer();

        expect(nodemailer.createTransport).toHaveBeenCalledWith(
            expect.objectContaining({ port: 465, secure: true, requireTLS: false }),
        );
    });

    it('skips STARTTLS when SMTP_REQUIRE_TLS is false', () => {
        process.env.SMTP_PORT = '1025';
        process.env.SMTP_REQUIRE_TLS = 'false';

        loadMailer();

        expect(nodemailer.createTransport).toHaveBeenCalledWith(
            expect.objectContaining({ port: 1025, secure: false, requireTLS: false }),
        );
    });

    it('authenticates when SMTP_USER is set', () => {
        process.env.SMTP_PORT = '2525';
        process.env.SMTP_USER = 'user@example.com';
        process.env.SMTP_PASS = 'app-password';

        loadMailer();

        expect(nodemailer.createTransport).toHaveBeenCalledWith(expect.objectContaining({
            port: 2525,
            secure: false,
            requireTLS: true,
            auth: { user: 'user@example.com', pass: 'app-password' },
        }));
    });

    it('falls back to port 587 when SMTP_PORT is not a number', () => {
        process.env.SMTP_PORT = 'abc';

        loadMailer();

        expect(nodemailer.createTransport).toHaveBeenCalledWith(expect.objectContaining({ port: 587 }));
    });

    it('logs when the SMTP server is ready', () => {
        loadMailer();

        const callback = transporter.verify.mock.calls[0][0];
        callback(null);

        expect(logSpy).toHaveBeenCalledWith('SMTP Server is ready to take our messages');
        expect(errorSpy).not.toHaveBeenCalled();
    });

    it('logs the SMTP connection error', () => {
        loadMailer();

        const error = new Error('ECONNREFUSED');
        transporter.verify.mock.calls[0][0](error);

        expect(errorSpy).toHaveBeenCalledWith('SMTP Connection Error:', error);
        expect(logSpy).not.toHaveBeenCalled();
    });
});
