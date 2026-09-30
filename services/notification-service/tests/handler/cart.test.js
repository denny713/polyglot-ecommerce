jest.mock('../../src/repository/order', () => ({ findProducts: jest.fn() }));
jest.mock('../../src/service/mail', () => ({ sendEmail: jest.fn() }));
jest.mock('../../src/handler/recipient', () => ({ requireRecipient: jest.fn() }));

const { findProducts } = require('../../src/repository/order');
const { sendEmail } = require('../../src/service/mail');
const { requireRecipient } = require('../../src/handler/recipient');
const { handleCartExpired } = require('../../src/handler/cart');
const { account } = require('../fixtures/order');

describe('handleCartExpired', () => {
    let logSpy;

    beforeEach(() => {
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        requireRecipient.mockResolvedValue(account);
        sendEmail.mockResolvedValue('<message-id>');
    });

    afterEach(() => logSpy.mockRestore());

    it('emails the customer every product that left their cart', async () => {
        findProducts.mockResolvedValue([
            { id: 7, name: 'Keyboard', sellPrice: '100000.00' },
            { id: 8, name: 'Mouse', sellPrice: '25000.00' },
        ]);

        await handleCartExpired({ userId: 'user-1', productIds: [7, 8] });

        expect(findProducts).toHaveBeenCalledWith([7, 8]);
        expect(requireRecipient).toHaveBeenCalledWith('user-1', 'Expired cart');
        const mail = sendEmail.mock.calls[0][0];
        expect(mail.to).toBe('jane@example.com');
        expect(mail.subject).toBe('2 items were removed from your ecommerce cart');
        expect(logSpy).toHaveBeenCalledWith('Sent CART_EXPIRED email for 2 product(s) to user user-1 (<message-id>)');
    });

    it('sends nothing when every product has been deleted since', async () => {
        findProducts.mockResolvedValue([]);

        await handleCartExpired({ userId: 'user-1', productIds: [7] });

        expect(requireRecipient).not.toHaveBeenCalled();
        expect(sendEmail).not.toHaveBeenCalled();
        expect(logSpy.mock.calls[0][0]).toContain('Skipped CART_EXPIRED email for user user-1');
    });
});
