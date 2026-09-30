const { handleCheckoutExpired, handlePaymentSucceeded } = require('./checkout');
const { handleRefundCancellation } = require('./refund');
const { UnprocessableEventError } = require('./error');

// Order events carry only the id of the row they are about; each handler reads
// the rest from the database.
const handlers = {
    CHECKOUT_EXPIRED: handleCheckoutExpired,
    PAYMENT_SUCCEEDED: handlePaymentSucceeded,
    REFUND_CANCELLATION: handleRefundCancellation,
};

const handleOrderEvent = async (event) => {
    const handle = handlers[event.eventType];
    if (!handle) {
        throw new UnprocessableEventError(`Unknown order event type: ${event.eventType}`);
    }

    if (event.id === undefined || event.id === null) {
        throw new UnprocessableEventError(`Order event ${event.eventId} has no id`);
    }

    await handle(event);
};

module.exports = { handleOrderEvent };
