const {
    SIGNATURE, formatMoney, formatDate, maskAccount, formatAccount, formatItems, greeting,
} = require('../../src/template/format');

// Intl separates the currency symbol with a no-break space.
const rp = (amount) => `Rp ${amount}`;

describe('format helpers', () => {
    it('formats NUMERIC strings as rupiah', () => {
        expect(formatMoney('150000.00')).toBe(rp('150.000,00'));
        expect(formatMoney('0')).toBe(rp('0,00'));
    });

    it('formats times in Jakarta time', () => {
        expect(formatDate(new Date('2026-09-30T03:00:00Z'))).toBe('30 September 2026 at 10:00 WIB');
    });

    it('keeps only the last four digits of an account number', () => {
        expect(maskAccount('1234567890')).toBe('****7890');
        expect(maskAccount('123')).toBe('****123');
    });

    it('describes an account with its bank and holder', () => {
        expect(formatAccount({ bankName: 'BCA', accountNumber: '1234567890', accountName: 'Jane' }))
            .toBe('BCA ****7890 (Jane)');
    });

    it('lists order lines', () => {
        expect(formatItems([
            { productName: 'Mouse', quantity: 2, unitPrice: '25000.00', subtotal: '50000.00' },
        ])).toBe(`    - Mouse x2 @ ${rp('25.000,00')} = ${rp('50.000,00')}`);
    });

    it('greets by first name, falling back to the username', () => {
        expect(greeting({ firstName: 'Jane', username: 'jane.doe' })).toBe('Hello Jane,');
        expect(greeting({ username: 'jane.doe' })).toBe('Hello jane.doe,');
    });

    it('signs off as the ecommerce team', () => {
        expect(SIGNATURE).toBe('\nRegards,\nEcommerce Team');
    });
});
