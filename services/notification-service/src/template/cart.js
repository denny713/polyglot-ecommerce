// Email templates for cart events published by order-service, keyed by eventType.
const { SIGNATURE, formatMoney, greeting } = require('./format');

// Several lines that expired close together arrive as one email.
const cartExpired = ({ account, products }) => {
    const one = products.length === 1;

    return {
        subject: one
            ? 'An item was removed from your ecommerce cart'
            : `${products.length} items were removed from your ecommerce cart`,
        text: `${greeting(account)}

${one ? 'This item was' : 'These items were'} left in your cart without being touched for a while, so ${one ? 'it has' : 'they have'} been removed:

${products.map((product) => `    - ${product.name} (${formatMoney(product.sellPrice)})`).join('\n')}

Prices shown are the current ones. Add ${one ? 'it' : 'them'} to your cart again whenever you are ready.
${SIGNATURE}`,
    };
};

module.exports = {
    CART_EXPIRED: cartExpired,
};
