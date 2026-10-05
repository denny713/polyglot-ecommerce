jest.mock('../../src/repository/order', () => ({ findRefund: jest.fn(), findSalesOrder: jest.fn() }));
jest.mock('../../src/service/mail', () => ({ sendEmail: jest.fn() }));
jest.mock('../../src/handler/recipient', () => ({ requireRecipient: jest.fn() }));

const { findRefund, findSalesOrder } = require('../../src/repository/order');
const { sendEmail } = require('../../src/service/mail');
const { requireRecipient } = require('../../src/handler/recipient');
const { handleRefundCancellation } = require('../../src/handler/refund');
const { UnprocessableEventError } = require('../../src/handler/error');
const { account, order, refund } = require('../fixtures/order');

describe('handleRefundCancellation', () => {
    let logSpy;

    beforeEach(() => {
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        requireRecipient.mockResolvedValue(account);
        sendEmail.mockResolvedValue('<message-id>');
    });

    afterEach(() => logSpy.mockRestore());

    it('emails the customer the refund of their cancelled order', async () => {
        findRefund.mockResolvedValue(refund());
        findSalesOrder.mockResolvedValue(order({ status: 'Cancelled' }));

        await handleRefundCancellation({ eventType: 'REFUND_CANCELLATION', id: 51 });

        expect(findRefund).toHaveBeenCalledWith(51);
        expect(findSalesOrder).toHaveBeenCalledWith(15);
        expect(requireRecipient).toHaveBeenCalledWith('user-1', 'Sales order SO20260930001');
        const mail = sendEmail.mock.calls[0][0];
        expect(mail.to).toBe('jane@example.com');
        expect(mail.subject).toBe('Refund RF20260930001 for your cancelled ecommerce order SO20260930001');
        expect(logSpy).toHaveBeenCalledWith('Sent REFUND_CANCELLATION email for refund RF20260930001 (<message-id>)');
    });

    it('drops an event for a refund that does not exist', async () => {
        findRefund.mockResolvedValue(null);

        const promise = handleRefundCancellation({ id: 99 });

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Refund 99 not found');
        expect(findSalesOrder).not.toHaveBeenCalled();
    });

    it('drops an event whose order does not exist', async () => {
        findRefund.mockResolvedValue(refund());
        findSalesOrder.mockResolvedValue(null);

        const promise = handleRefundCancellation({ id: 51 });

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Sales order 15 of refund RF20260930001 not found');
        expect(sendEmail).not.toHaveBeenCalled();
    });
});
