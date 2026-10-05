// Shared by the order-side templates. Amounts are rupiah and times are shown in
// Jakarta time, the zone the order service records them in.

const SIGNATURE = `
Regards,
Ecommerce Team`;

const money = new Intl.NumberFormat('id-ID', { style: 'currency', currency: 'IDR' });

const dateTime = new Intl.DateTimeFormat('en-GB', {
    dateStyle: 'long',
    timeStyle: 'short',
    timeZone: 'Asia/Jakarta',
});

const formatMoney = (value) => money.format(Number(value));

const formatDate = (value) => `${dateTime.format(new Date(value))} WIB`;

// Only the last four digits: enough for the reader to recognise the account,
// not enough to be worth anything to whoever else reads the email.
const maskAccount = (accountNumber) => `****${String(accountNumber).slice(-4)}`;

const formatAccount = ({ bankName, accountNumber, accountName }) =>
    `${bankName} ${maskAccount(accountNumber)} (${accountName})`;

const formatItems = (items) => items
    .map((item) => `    - ${item.productName} x${item.quantity} @ ${formatMoney(item.unitPrice)}`
        + ` = ${formatMoney(item.subtotal)}`)
    .join('\n');

const greeting = (account) => `Hello ${account.firstName || account.username},`;

module.exports = { SIGNATURE, formatMoney, formatDate, maskAccount, formatAccount, formatItems, greeting };
