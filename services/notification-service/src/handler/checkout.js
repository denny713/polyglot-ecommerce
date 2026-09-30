const templates = require('../template/checkout');
const { findSalesOrder, findPayment, findRefundsOfOrder } = require('../repository/order');
const { sendEmail } = require('../service/mail');
const { requireRecipient } = require('./recipient');
const { UnprocessableEventError } = require('./error');

const requireSalesOrder = async (id) => {
    const order = await findSalesOrder(id);
    if (!order) {
        throw new UnprocessableEventError(`Sales order ${id} not found`);
    }

    return order;
};

// A pending order that ran out of time to be paid, with whatever it refunded.
const handleCheckoutExpired = async (event) => {
    const order = await requireSalesOrder(event.id);
    const account = await requireRecipient(order.createdBy, `Sales order ${order.documentNumber}`);
    const refunds = await findRefundsOfOrder(order.id, 'Expired');

    const { subject, text } = templates.CHECKOUT_EXPIRED({ account, order, refunds });
    const messageId = await sendEmail({ to: account.email, subject, text });

    console.log(`Sent CHECKOUT_EXPIRED email for sales order ${order.documentNumber} (${messageId})`);
};

// A payment towards an order, whether it cleared the order or was one instalment.
const handlePaymentSucceeded = async (event) => {
    const payment = await findPayment(event.id);
    if (!payment) {
        throw new UnprocessableEventError(`Payment ${event.id} not found`);
    }

    const order = await requireSalesOrder(payment.salesOrderId);
    const account = await requireRecipient(order.createdBy, `Sales order ${order.documentNumber}`);

    const { subject, text } = templates.PAYMENT_SUCCEEDED({ account, order, payment });
    const messageId = await sendEmail({ to: account.email, subject, text });

    console.log(`Sent PAYMENT_SUCCEEDED email for payment ${payment.documentNumber} (${messageId})`);
};

module.exports = { handleCheckoutExpired, handlePaymentSucceeded };
