// Email templates for refund events published by order-service, keyed by eventType.
const {
    SIGNATURE, formatMoney, formatDate, formatAccount, formatItems, greeting,
} = require('./format');

const refundCancellation = ({ account, order, refund }) => ({
    subject: `Refund ${refund.documentNumber} for your cancelled ecommerce order ${order.documentNumber}`,
    text: `${greeting(account)}

Your order ${order.documentNumber} was cancelled, and on ${formatDate(refund.refundedAt)} we refunded what you paid towards it.

    Refund number:  ${refund.documentNumber}
    Amount:         ${formatMoney(refund.amount)}
    Refunded to:    ${formatAccount(refund)}

The cancelled order:

${formatItems(order.items)}

    Grand total:  ${formatMoney(order.grandTotal)}

Depending on your bank, it may take a few days for the money to show in your account. If you did not cancel this order, contact us straight away.
${SIGNATURE}`,
});

module.exports = {
    REFUND_CANCELLATION: refundCancellation,
};
