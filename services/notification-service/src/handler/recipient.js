const { getAccount } = require('../service/account');
const { UnprocessableEventError } = require('./error');

// The customer an order-side email goes to. One deleted since, or left without
// an address, has nobody to mail, which no retry will change.
const requireRecipient = async (userId, subject) => {
    if (!userId) {
        throw new UnprocessableEventError(`${subject} has no customer`);
    }

    const account = await getAccount(userId);
    if (!account) {
        throw new UnprocessableEventError(`${subject} belongs to user ${userId}, who no longer exists`);
    }

    if (!account.email) {
        throw new UnprocessableEventError(`${subject} belongs to user ${userId}, who has no email address`);
    }

    return account;
};

module.exports = { requireRecipient };
