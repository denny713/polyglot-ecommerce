jest.mock('../../src/model', () => {
    const unscopedProduct = { name: 'UnscopedProduct' };
    return {
        Product: { findAll: jest.fn(), unscoped: jest.fn(() => unscopedProduct) },
        SalesOrder: { findByPk: jest.fn() },
        SalesOrderDetail: { name: 'SalesOrderDetail' },
        Payment: { findByPk: jest.fn() },
        Refund: { findByPk: jest.fn(), findAll: jest.fn() },
    };
});

const { Op } = require('sequelize');
const {
    Product, SalesOrder, SalesOrderDetail, Payment, Refund,
} = require('../../src/model');
const {
    findSalesOrder, findPayment, findRefund, findRefundsOfOrder, findProducts,
} = require('../../src/repository/order');

const createdAt = new Date('2026-09-30T03:00:00Z');
const updatedAt = new Date('2026-09-30T04:00:00Z');

// Model instances expose their attributes as properties, so plain objects of
// the same shape stand in for them. BIGINT columns come back as strings.
const orderRow = (details) => ({
    id: '15',
    documentNumber: 'SO20260930001',
    status: 'Expired',
    grandTotal: '150000.00',
    paid: '40000.00',
    outstanding: '110000.00',
    createdBy: 'user-1',
    createdAt,
    updatedAt,
    details,
});

const refundRow = {
    id: '51',
    documentNumber: 'RF20260930001',
    salesOrderId: '15',
    paymentId: '41',
    reason: 'Cancellation',
    amount: '40000.00',
    bankName: 'BCA',
    accountNumber: '1234567890',
    accountName: 'Jane',
    refundedAt: updatedAt,
};

const refund = {
    id: 51,
    documentNumber: 'RF20260930001',
    salesOrderId: 15,
    paymentId: 41,
    reason: 'Cancellation',
    amount: '40000.00',
    bankName: 'BCA',
    accountNumber: '1234567890',
    accountName: 'Jane',
    refundedAt: updatedAt,
};

describe('order repository', () => {
    describe('findSalesOrder', () => {
        it('returns the order with its lines', async () => {
            SalesOrder.findByPk.mockResolvedValue(orderRow([
                {
                    quantity: 2, unitPrice: '75000.00', subtotal: '150000.00', product: { name: 'Keyboard' },
                },
            ]));

            await expect(findSalesOrder(15)).resolves.toEqual({
                id: 15,
                documentNumber: 'SO20260930001',
                status: 'Expired',
                grandTotal: '150000.00',
                paid: '40000.00',
                outstanding: '110000.00',
                createdBy: 'user-1',
                createdAt,
                updatedAt,
                items: [{ productName: 'Keyboard', quantity: 2, unitPrice: '75000.00', subtotal: '150000.00' }],
            });
        });

        it('loads the lines in order with their products, deleted or not', async () => {
            SalesOrder.findByPk.mockResolvedValue(orderRow([]));

            await findSalesOrder(15);

            expect(SalesOrder.findByPk).toHaveBeenCalledWith(15, {
                include: [{
                    model: SalesOrderDetail,
                    as: 'details',
                    required: false,
                    include: [{ model: Product.unscoped(), as: 'product' }],
                }],
                order: [[{ model: SalesOrderDetail, as: 'details' }, 'id', 'ASC']],
            });
        });

        it('copes with an order whose lines or product are missing', async () => {
            SalesOrder.findByPk.mockResolvedValueOnce(orderRow(undefined));
            await expect(findSalesOrder(15)).resolves.toMatchObject({ items: [] });

            SalesOrder.findByPk.mockResolvedValueOnce(orderRow([{ quantity: 1, unitPrice: '1', subtotal: '1', product: null }]));
            await expect(findSalesOrder(15)).resolves.toMatchObject({ items: [{ productName: undefined }] });
        });

        it('returns null for an order that does not exist', async () => {
            SalesOrder.findByPk.mockResolvedValue(null);

            await expect(findSalesOrder(99)).resolves.toBeNull();
        });
    });

    describe('findPayment', () => {
        it('returns the payment', async () => {
            Payment.findByPk.mockResolvedValue({
                id: '41',
                documentNumber: 'PY20260930001',
                salesOrderId: '15',
                reference: 'TRX-001',
                method: 'Transfer',
                bankName: 'BCA',
                accountNumber: '1234567890',
                accountName: 'Jane',
                amount: '50000.00',
                appliedAmount: '40000.00',
                excessAmount: '10000.00',
                paidAt: updatedAt,
            });

            await expect(findPayment(41)).resolves.toEqual({
                id: 41,
                documentNumber: 'PY20260930001',
                salesOrderId: 15,
                reference: 'TRX-001',
                method: 'Transfer',
                bankName: 'BCA',
                accountNumber: '1234567890',
                accountName: 'Jane',
                amount: '50000.00',
                appliedAmount: '40000.00',
                excessAmount: '10000.00',
                paidAt: updatedAt,
            });
            expect(Payment.findByPk).toHaveBeenCalledWith(41);
        });

        it('returns null for a payment that does not exist', async () => {
            Payment.findByPk.mockResolvedValue(null);

            await expect(findPayment(99)).resolves.toBeNull();
        });
    });

    describe('findRefund', () => {
        it('returns the refund', async () => {
            Refund.findByPk.mockResolvedValue(refundRow);

            await expect(findRefund(51)).resolves.toEqual(refund);
            expect(Refund.findByPk).toHaveBeenCalledWith(51);
        });

        it('returns null for a refund that does not exist', async () => {
            Refund.findByPk.mockResolvedValue(null);

            await expect(findRefund(99)).resolves.toBeNull();
        });
    });

    describe('findRefundsOfOrder', () => {
        it('returns the refunds of one reason, oldest first', async () => {
            Refund.findAll.mockResolvedValue([refundRow]);

            await expect(findRefundsOfOrder(15, 'Expired')).resolves.toEqual([refund]);
            expect(Refund.findAll).toHaveBeenCalledWith({
                where: { salesOrderId: 15, reason: 'Expired' },
                order: [['id', 'ASC']],
            });
        });
    });

    describe('findProducts', () => {
        it('returns the products that still exist, by name', async () => {
            Product.findAll.mockResolvedValue([{ id: '7', name: 'Keyboard', sellPrice: '75000.00' }]);

            await expect(findProducts([7, 8])).resolves.toEqual([{ id: 7, name: 'Keyboard', sellPrice: '75000.00' }]);
            expect(Product.findAll).toHaveBeenCalledWith({
                where: { id: { [Op.in]: [7, 8] } },
                order: [['name', 'ASC']],
            });
        });
    });
});
