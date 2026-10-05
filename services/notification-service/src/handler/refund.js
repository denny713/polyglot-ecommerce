const templates = require('../template/refund');
const { findRefund, findSalesOrder } = require('../repository/order');
const { sendEmail } = require('../service/mail');
const { requireRecipient } = require('./recipient');
const { UnprocessableEventError } = require('./error');

// Money returned because the customer cancelled their order.
const handleRefundCancellation = async (event) => {
    const refund = await findRefund(event.id);
    if (!refund) {
        throw new UnprocessableEventError(`Refund ${event.id} not found`);
    }

    const order = await findSalesOrder(refund.salesOrderId);
    if (!order) {
        throw new UnprocessableEventError(`Sales order ${refund.salesOrderId} of refund ${refund.documentNumber} not found`);
    }

    const account = await requireRecipient(order.createdBy, `Sales order ${order.documentNumber}`);

    const { subject, text } = templates.REFUND_CANCELLATION({ account, order, refund });
    const messageId = await sendEmail({ to: account.email, subject, text });

    console.log(`Sent REFUND_CANCELLATION email for refund ${refund.documentNumber} (${messageId})`);
};

module.exports = { handleRefundCancellation };
