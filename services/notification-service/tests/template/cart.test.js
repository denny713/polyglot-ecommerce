const templates = require('../../src/template/cart');
const { formatMoney } = require('../../src/template/format');
const { account } = require('../fixtures/order');

describe('cart templates', () => {
    it('exposes a template for every cart event type', () => {
        expect(Object.keys(templates)).toEqual(['CART_EXPIRED']);
    });

    it('tells about a single expired product', () => {
        const { subject, text } = templates.CART_EXPIRED({
            account,
            products: [{ id: 7, name: 'Keyboard', sellPrice: '100000.00' }],
        });

        expect(subject).toBe('An item was removed from your ecommerce cart');
        expect(text).toContain('Hello Jane,');
        expect(text).toContain('This item was left in your cart without being touched for a while, so it has been removed:');
        expect(text).toContain(`    - Keyboard (${formatMoney('100000.00')})`);
        expect(text).toContain('Add it to your cart again');
        expect(text).toMatch(/Regards,\nEcommerce Team$/);
    });

    it('lists several expired products in one email', () => {
        const { subject, text } = templates.CART_EXPIRED({
            account,
            products: [
                { id: 7, name: 'Keyboard', sellPrice: '100000.00' },
                { id: 8, name: 'Mouse', sellPrice: '25000.00' },
            ],
        });

        expect(subject).toBe('2 items were removed from your ecommerce cart');
        expect(text).toContain('These items were left in your cart without being touched for a while, so they have been removed:');
        expect(text).toContain(`    - Keyboard (${formatMoney('100000.00')})\n    - Mouse (${formatMoney('25000.00')})`);
        expect(text).toContain('Add them to your cart again');
    });
});
