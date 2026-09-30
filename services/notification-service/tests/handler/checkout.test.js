jest.mock('../../src/repository/order', () => ({
    findSalesOrder: jest.fn(),
    findPayment: jest.fn(),
    findRefundsOfOrder: jest.fn(),
}));
jest.mock('../../src/service/mail', () => ({ sendEmail: jest.fn() }));
jest.mock('../../src/handler/recipient', () => ({ requireRecipient: jest.fn() }));

const { findSalesOrder, findPayment, findRefundsOfOrder } = require('../../src/repository/order');
const { sendEmail } = require('../../src/service/mail');
const { requireRecipient } = require('../../src/handler/recipient');
const { handleCheckoutExpired, handlePaymentSucceeded } = require('../../src/handler/checkout');
const { UnprocessableEventError } = require('../../src/handler/error');
const {
    account, order, payment, refund,
} = require('../fixtures/order');

describe('checkout handlers', () => {
    let logSpy;

    beforeEach(() => {
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        requireRecipient.mockResolvedValue(account);
        sendEmail.mockResolvedValue('<message-id>');
    });

    afterEach(() => logSpy.mockRestore());

    describe('handleCheckoutExpired', () => {
        it('emails the customer the expired order and its refunds', async () => {
            findSalesOrder.mockResolvedValue(order({ paid: '40000.00' }));
            findRefundsOfOrder.mockResolvedValue([refund({ reason: 'Expired', amount: '40000.00' })]);

            await handleCheckoutExpired({ eventType: 'CHECKOUT_EXPIRED', id: 15 });

            expect(findSalesOrder).toHaveBeenCalledWith(15);
            expect(requireRecipient).toHaveBeenCalledWith('user-1', 'Sales order SO20260930001');
            expect(findRefundsOfOrder).toHaveBeenCalledWith(15, 'Expired');
            const mail = sendEmail.mock.calls[0][0];
            expect(mail.to).toBe('jane@example.com');
            expect(mail.subject).toBe('Your ecommerce order SO20260930001 has expired');
            expect(mail.text).toContain('RF20260930001');
            expect(logSpy).toHaveBeenCalledWith('Sent CHECKOUT_EXPIRED email for sales order SO20260930001 (<message-id>)');
        });

        it('drops an event for an order that does not exist', async () => {
            findSalesOrder.mockResolvedValue(null);

            const promise = handleCheckoutExpired({ id: 99 });

            await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
            await expect(promise).rejects.toThrow('Sales order 99 not found');
            expect(sendEmail).not.toHaveBeenCalled();
        });

        it('does not look for refunds without a customer to mail', async () => {
            findSalesOrder.mockResolvedValue(order());
            requireRecipient.mockRejectedValue(new UnprocessableEventError('no customer'));

            await expect(handleCheckoutExpired({ id: 15 })).rejects.toThrow('no customer');
            expect(findRefundsOfOrder).not.toHaveBeenCalled();
            expect(sendEmail).not.toHaveBeenCalled();
        });
    });

    describe('handlePaymentSucceeded', () => {
        it('emails the customer the payment and the order it went to', async () => {
            findPayment.mockResolvedValue(payment());
            findSalesOrder.mockResolvedValue(order({ status: 'Paid', paid: '150000.00', outstanding: '0.00' }));

            await handlePaymentSucceeded({ eventType: 'PAYMENT_SUCCEEDED', id: 41 });

            expect(findPayment).toHaveBeenCalledWith(41);
            expect(findSalesOrder).toHaveBeenCalledWith(15);
            const mail = sendEmail.mock.calls[0][0];
            expect(mail.to).toBe('jane@example.com');
            expect(mail.subject).toBe('Payment received, your ecommerce order SO20260930001 is paid');
            expect(logSpy).toHaveBeenCalledWith('Sent PAYMENT_SUCCEEDED email for payment PY20260930001 (<message-id>)');
        });

        it('drops an event for a payment that does not exist', async () => {
            findPayment.mockResolvedValue(null);

            const promise = handlePaymentSucceeded({ id: 99 });

            await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
            await expect(promise).rejects.toThrow('Payment 99 not found');
            expect(findSalesOrder).not.toHaveBeenCalled();
        });

        it('drops an event whose order does not exist', async () => {
            findPayment.mockResolvedValue(payment());
            findSalesOrder.mockResolvedValue(null);

            await expect(handlePaymentSucceeded({ id: 41 })).rejects.toThrow('Sales order 15 not found');
            expect(sendEmail).not.toHaveBeenCalled();
        });

        it('lets a mail failure through, so the event is retried', async () => {
            findPayment.mockResolvedValue(payment());
            findSalesOrder.mockResolvedValue(order());
            sendEmail.mockRejectedValue(new Error('SMTP down'));

            await expect(handlePaymentSucceeded({ id: 41 })).rejects.toThrow('SMTP down');
            expect(logSpy).not.toHaveBeenCalled();
        });
    });
});
