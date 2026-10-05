const transporter = require('../configuration/mailer');

const sendEmail = async ({ to, subject, text }) => {
    const info = await transporter.sendMail({
        from: process.env.MAIL_FROM || process.env.SMTP_USER,
        to,
        subject,
        text,
    });

    return info.messageId;
};

module.exports = { sendEmail };
