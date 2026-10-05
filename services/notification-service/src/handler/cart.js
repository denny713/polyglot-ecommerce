const templates = require('../template/cart');
const { findProducts } = require('../repository/order');
const { sendEmail } = require('../service/mail');
const { requireRecipient } = require('./recipient');

// One email for every cart line of a customer that expired close together.
const handleCartExpired = async ({ userId, productIds }) => {
    const products = await findProducts(productIds);
    if (products.length === 0) {
        // Every product was deleted since: there is nothing left worth telling.
        console.log(`Skipped CART_EXPIRED email for user ${userId}: none of products ${productIds} exist any more`);
        return;
    }

    const account = await requireRecipient(userId, 'Expired cart');
    const { subject, text } = templates.CART_EXPIRED({ account, products });
    const messageId = await sendEmail({ to: account.email, subject, text });

    console.log(`Sent CART_EXPIRED email for ${products.length} product(s) to user ${userId} (${messageId})`);
};

module.exports = { handleCartExpired };
