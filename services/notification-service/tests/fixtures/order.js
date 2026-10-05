// Rows as the order repository returns them, shared by the template and handler tests.

const account = {
    id: 'user-1',
    username: 'jane.doe',
    email: 'jane@example.com',
    firstName: 'Jane',
    lastName: 'Doe',
};

const order = (overrides = {}) => ({
    id: 15,
    documentNumber: 'SO20260930001',
    status: 'Expired',
    grandTotal: '150000.00',
    paid: '0.00',
    outstanding: '150000.00',
    createdBy: 'user-1',
    createdAt: new Date('2026-09-30T02:00:00Z'),
    updatedAt: new Date('2026-09-30T03:00:00Z'),
    items: [
        { productName: 'Keyboard', quantity: 1, unitPrice: '100000.00', subtotal: '100000.00' },
        { productName: 'Mouse', quantity: 2, unitPrice: '25000.00', subtotal: '50000.00' },
    ],
    ...overrides,
});

const payment = (overrides = {}) => ({
    id: 41,
    documentNumber: 'PY20260930001',
    salesOrderId: 15,
    reference: 'TRX-001',
    method: 'Transfer',
    bankName: 'BCA',
    accountNumber: '1234567890',
    accountName: 'Jane Doe',
    amount: '150000.00',
    appliedAmount: '150000.00',
    excessAmount: '0.00',
    paidAt: new Date('2026-09-30T02:30:00Z'),
    ...overrides,
});

const refund = (overrides = {}) => ({
    id: 51,
    documentNumber: 'RF20260930001',
    salesOrderId: 15,
    paymentId: 41,
    reason: 'Cancellation',
    amount: '150000.00',
    bankName: 'BCA',
    accountNumber: '1234567890',
    accountName: 'Jane Doe',
    refundedAt: new Date('2026-09-30T05:00:00Z'),
    ...overrides,
});

module.exports = { account, order, payment, refund };
