const templates = require('../../src/template/refund');
const { formatMoney, formatDate } = require('../../src/template/format');
const { account, order, refund } = require('../fixtures/order');

describe('refund templates', () => {
    it('exposes a template for every refund event type', () => {
        expect(Object.keys(templates)).toEqual(['REFUND_CANCELLATION']);
    });

    it('details the refund of a cancelled order', () => {
        const refunded = refund();
        const { subject, text } = templates.REFUND_CANCELLATION({
            account,
            order: order({ status: 'Cancelled' }),
            refund: refunded,
        });

        expect(subject).toBe('Refund RF20260930001 for your cancelled ecommerce order SO20260930001');
        expect(text).toContain('Hello Jane,');
        expect(text).toContain(`on ${formatDate(refunded.refundedAt)} we refunded`);
        expect(text).toContain('Refund number:  RF20260930001');
        expect(text).toContain(`Amount:         ${formatMoney('150000.00')}`);
        expect(text).toContain('Refunded to:    BCA ****7890 (Jane Doe)');
        expect(text).toContain(`    - Mouse x2 @ ${formatMoney('25000.00')} = ${formatMoney('50000.00')}`);
        expect(text).not.toContain('1234567890');
        expect(text).toMatch(/Regards,\nEcommerce Team$/);
    });
});
