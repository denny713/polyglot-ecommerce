jest.mock('../../src/handler/checkout', () => ({
    handleCheckoutExpired: jest.fn(),
    handlePaymentSucceeded: jest.fn(),
}));
jest.mock('../../src/handler/refund', () => ({ handleRefundCancellation: jest.fn() }));

const { handleCheckoutExpired, handlePaymentSucceeded } = require('../../src/handler/checkout');
const { handleRefundCancellation } = require('../../src/handler/refund');
const { handleOrderEvent } = require('../../src/handler/order');
const { UnprocessableEventError } = require('../../src/handler/error');

describe('handleOrderEvent', () => {
    it.each([
        ['CHECKOUT_EXPIRED', handleCheckoutExpired],
        ['PAYMENT_SUCCEEDED', handlePaymentSucceeded],
        ['REFUND_CANCELLATION', handleRefundCancellation],
    ])('hands %s to its handler', async (eventType, handler) => {
        const event = { eventId: 'evt-1', eventType, id: 15 };

        await handleOrderEvent(event);

        expect(handler).toHaveBeenCalledWith(event);
    });

    it('accepts an id of zero', async () => {
        await handleOrderEvent({ eventType: 'PAYMENT_SUCCEEDED', id: 0 });

        expect(handlePaymentSucceeded).toHaveBeenCalled();
    });

    it('rejects an unknown event type', async () => {
        const promise = handleOrderEvent({ eventType: 'ORDER_SHIPPED', id: 15 });

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Unknown order event type: ORDER_SHIPPED');
    });

    it.each([undefined, null])('rejects an event whose id is %s', async (id) => {
        const promise = handleOrderEvent({ eventId: 'evt-1', eventType: 'CHECKOUT_EXPIRED', id });

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Order event evt-1 has no id');
        expect(handleCheckoutExpired).not.toHaveBeenCalled();
    });

    it('lets a handler failure through', async () => {
        handleRefundCancellation.mockRejectedValue(new Error('SMTP down'));

        await expect(handleOrderEvent({ eventType: 'REFUND_CANCELLATION', id: 51 })).rejects.toThrow('SMTP down');
    });
});
