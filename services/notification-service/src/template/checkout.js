// Email templates for checkout and payment events published by order-service,
// keyed by eventType.
const {
    SIGNATURE, formatMoney, formatDate, formatAccount, formatItems, greeting,
} = require('./format');

const checkoutExpired = ({ account, order, refunds }) => {
    const refunded = refunds.length === 0 ? '' : `
What you paid towards it has been refunded to the account it came from:

${refunds.map((refund) => `    - ${refund.documentNumber}: ${formatMoney(refund.amount)} to ${formatAccount(refund)}`).join('\n')}
`;

    return {
        subject: `Your ecommerce order ${order.documentNumber} has expired`,
        text: `${greeting(account)}

Your order ${order.documentNumber} was not paid in time and expired on ${formatDate(order.updatedAt)}. The items it was holding have been released:

${formatItems(order.items)}

    Grand total:  ${formatMoney(order.grandTotal)}
${refunded}
If you still want these items, please place a new order.
${SIGNATURE}`,
    };
};

const paymentSucceeded = ({ account, order, payment }) => {
    const settled = Number(order.outstanding) === 0;
    const excess = Number(payment.excessAmount) > 0
        ? `    Overpaid:           ${formatMoney(payment.excessAmount)} (refunded to the same account)\n`
        : '';

    return {
        subject: settled
            ? `Payment received, your ecommerce order ${order.documentNumber} is paid`
            : `Payment received for your ecommerce order ${order.documentNumber}`,
        text: `${greeting(account)}

We received your payment ${payment.documentNumber} for order ${order.documentNumber} on ${formatDate(payment.paidAt)}.

    Reference:          ${payment.reference}
    Method:             ${payment.method}, ${formatAccount(payment)}
    Amount:             ${formatMoney(payment.amount)}
    Applied to order:   ${formatMoney(payment.appliedAmount)}
${excess}
Order summary:

${formatItems(order.items)}

    Grand total:  ${formatMoney(order.grandTotal)}
    Paid:         ${formatMoney(order.paid)}
    Outstanding:  ${formatMoney(order.outstanding)}

${settled
        ? 'Your order is fully paid and will be processed shortly.'
        : `Please pay the remaining ${formatMoney(order.outstanding)} before the payment window closes, or the order will expire.`}
${SIGNATURE}`,
    };
};

module.exports = {
    CHECKOUT_EXPIRED: checkoutExpired,
    PAYMENT_SUCCEEDED: paymentSucceeded,
};
