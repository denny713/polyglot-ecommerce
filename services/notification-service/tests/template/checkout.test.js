const templates = require('../../src/template/checkout');
const { formatMoney, formatDate } = require('../../src/template/format');
const {
    account, order, payment, refund,
} = require('../fixtures/order');

describe('checkout templates', () => {
    it('exposes a template for every checkout event type', () => {
        expect(Object.keys(templates).sort()).toEqual(['CHECKOUT_EXPIRED', 'PAYMENT_SUCCEEDED']);
    });

    describe('CHECKOUT_EXPIRED', () => {
        it('lists what the expired order held', () => {
            const expired = order();
            const { subject, text } = templates.CHECKOUT_EXPIRED({ account, order: expired, refunds: [] });

            expect(subject).toBe('Your ecommerce order SO20260930001 has expired');
            expect(text).toContain('Hello Jane,');
            expect(text).toContain(`expired on ${formatDate(expired.updatedAt)}`);
            expect(text).toContain(`    - Keyboard x1 @ ${formatMoney('100000.00')} = ${formatMoney('100000.00')}`);
            expect(text).toContain(`    - Mouse x2 @ ${formatMoney('25000.00')} = ${formatMoney('50000.00')}`);
            expect(text).toContain(`Grand total:  ${formatMoney('150000.00')}`);
            expect(text).not.toContain('refunded');
            expect(text).toMatch(/Regards,\nEcommerce Team$/);
        });

        it('says what was refunded for an order paid in part', () => {
            const { text } = templates.CHECKOUT_EXPIRED({
                account,
                order: order({ paid: '40000.00' }),
                refunds: [refund({ reason: 'Expired', amount: '40000.00' })],
            });

            expect(text).toContain('What you paid towards it has been refunded to the account it came from:');
            expect(text).toContain(`    - RF20260930001: ${formatMoney('40000.00')} to BCA ****7890 (Jane Doe)`);
        });
    });

    describe('PAYMENT_SUCCEEDED', () => {
        it('confirms a payment that settles the order', () => {
            const paid = payment();
            const { subject, text } = templates.PAYMENT_SUCCEEDED({
                account,
                order: order({ status: 'Paid', paid: '150000.00', outstanding: '0.00' }),
                payment: paid,
            });

            expect(subject).toBe('Payment received, your ecommerce order SO20260930001 is paid');
            expect(text).toContain(`We received your payment PY20260930001 for order SO20260930001 on ${formatDate(paid.paidAt)}.`);
            expect(text).toContain('Reference:          TRX-001');
            expect(text).toContain('Method:             Transfer, BCA ****7890 (Jane Doe)');
            expect(text).toContain(`Amount:             ${formatMoney('150000.00')}`);
            expect(text).toContain(`Outstanding:  ${formatMoney('0.00')}`);
            expect(text).toContain('Your order is fully paid and will be processed shortly.');
            expect(text).not.toContain('Overpaid');
            expect(text).not.toContain('1234567890');
        });

        it('asks for the rest after an instalment', () => {
            const { subject, text } = templates.PAYMENT_SUCCEEDED({
                account,
                order: order({ status: 'Pending', paid: '40000.00', outstanding: '110000.00' }),
                payment: payment({ amount: '40000.00', appliedAmount: '40000.00' }),
            });

            expect(subject).toBe('Payment received for your ecommerce order SO20260930001');
            expect(text).toContain(`Please pay the remaining ${formatMoney('110000.00')} before the payment window closes`);
        });

        it('mentions an overpayment and its refund', () => {
            const { text } = templates.PAYMENT_SUCCEEDED({
                account,
                order: order({ status: 'Paid', paid: '150000.00', outstanding: '0.00' }),
                payment: payment({ amount: '200000.00', excessAmount: '50000.00' }),
            });

            expect(text).toContain(`Overpaid:           ${formatMoney('50000.00')} (refunded to the same account)`);
        });
    });
});
