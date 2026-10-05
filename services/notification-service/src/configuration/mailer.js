const nodemailer = require('nodemailer');

const port = Number(process.env.SMTP_PORT) || 587;

// Mirrors the Spring Boot mail config that is known to work against Gmail:
// port 587, mail.smtp.auth=true, mail.smtp.starttls.enable=true. Port 465 is
// implicit TLS instead, so STARTTLS only applies to the other ports.
// SMTP_REQUIRE_TLS=false turns STARTTLS off for a plain local catcher such as
// Mailpit in the docker-compose stack, which offers no TLS at all.
const requireTLS = port !== 465 && process.env.SMTP_REQUIRE_TLS !== 'false';

const transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port,
    secure: port === 465,
    requireTLS,
    auth: process.env.SMTP_USER
        ? { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS }
        : undefined,
});

transporter.verify((error) => {
    if (error) {
        console.error('SMTP Connection Error:', error);
    } else {
        console.log('SMTP Server is ready to take our messages');
    }
});

module.exports = transporter;
