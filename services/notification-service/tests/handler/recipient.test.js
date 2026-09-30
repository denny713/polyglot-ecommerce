jest.mock('../../src/service/account', () => ({ getAccount: jest.fn() }));

const { getAccount } = require('../../src/service/account');
const { requireRecipient } = require('../../src/handler/recipient');
const { UnprocessableEventError } = require('../../src/handler/error');
const { account } = require('../fixtures/order');

describe('requireRecipient', () => {
    it('returns the account of the customer', async () => {
        getAccount.mockResolvedValue(account);

        await expect(requireRecipient('user-1', 'Sales order SO1')).resolves.toBe(account);
        expect(getAccount).toHaveBeenCalledWith('user-1');
    });

    it('rejects a subject with no customer', async () => {
        const promise = requireRecipient(null, 'Sales order SO1');

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Sales order SO1 has no customer');
        expect(getAccount).not.toHaveBeenCalled();
    });

    it('rejects a customer who no longer exists', async () => {
        getAccount.mockResolvedValue(null);

        const promise = requireRecipient('user-1', 'Sales order SO1');

        await expect(promise).rejects.toBeInstanceOf(UnprocessableEventError);
        await expect(promise).rejects.toThrow('Sales order SO1 belongs to user user-1, who no longer exists');
    });

    it('rejects a customer without an email address', async () => {
        getAccount.mockResolvedValue({ ...account, email: undefined });

        await expect(requireRecipient('user-1', 'Sales order SO1'))
            .rejects.toThrow('Sales order SO1 belongs to user user-1, who has no email address');
    });

    it('lets a Keycloak outage through, so the event is retried', async () => {
        getAccount.mockRejectedValue(new Error('Keycloak down'));

        const promise = requireRecipient('user-1', 'Sales order SO1');

        await expect(promise).rejects.toThrow('Keycloak down');
        await expect(promise).rejects.not.toBeInstanceOf(UnprocessableEventError);
    });
});
