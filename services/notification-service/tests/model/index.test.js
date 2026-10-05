// A real Sequelize instance that never connects: defining models and building
// queries needs no database.
jest.mock('../../src/configuration/database', () => {
    const { Sequelize } = jest.requireActual('sequelize');
    return {
        sequelize: new Sequelize('postgres://user:pass@localhost:5432/ecommerce', {
            logging: false,
            define: { underscored: true, freezeTableName: true, timestamps: false },
        }),
    };
});

const {
    sequelize, Product, SalesOrder, SalesOrderDetail, Payment, Refund,
} = require('../../src/model');

const fieldsOf = (model) => Object.fromEntries(
    Object.entries(model.getAttributes()).map(([name, attribute]) => [name, attribute.field]),
);

const baseFields = {
    id: 'id',
    isActive: 'is_active',
    isDeleted: 'is_deleted',
    createdBy: 'created_by',
    updatedBy: 'updated_by',
    createdAt: 'created_at',
    updatedAt: 'updated_at',
};

describe('models', () => {
    it('shares the one Sequelize instance', () => {
        expect(SalesOrder.sequelize).toBe(sequelize);
    });

    it.each([
        [Product, 'product'],
        [SalesOrder, 'sales_order'],
        [SalesOrderDetail, 'sales_order_detail'],
        [Payment, 'payment'],
        [Refund, 'refund'],
    ])('maps %s to the %s table', (model, table) => {
        expect(model.getTableName()).toBe(table);
    });

    it('maps the columns of every table the migrations create', () => {
        expect(fieldsOf(Product)).toEqual({
            ...baseFields, name: 'name', description: 'description', sellPrice: 'sell_price', imageUrl: 'image_url',
        });
        expect(fieldsOf(SalesOrder)).toEqual({
            ...baseFields,
            documentNumber: 'document_number',
            status: 'status',
            grandTotal: 'grand_total',
            paid: 'paid',
            outstanding: 'outstanding',
        });
        expect(fieldsOf(SalesOrderDetail)).toEqual({
            ...baseFields,
            salesOrderId: 'sales_order_id',
            productId: 'product_id',
            quantity: 'quantity',
            unitPrice: 'unit_price',
            subtotal: 'subtotal',
        });
        expect(fieldsOf(Payment)).toEqual({
            ...baseFields,
            documentNumber: 'document_number',
            salesOrderId: 'sales_order_id',
            reference: 'reference',
            method: 'method',
            bankName: 'bank_name',
            accountNumber: 'account_number',
            accountName: 'account_name',
            amount: 'amount',
            appliedAmount: 'applied_amount',
            excessAmount: 'excess_amount',
            paidAt: 'paid_at',
        });
        expect(fieldsOf(Refund)).toEqual({
            ...baseFields,
            documentNumber: 'document_number',
            salesOrderId: 'sales_order_id',
            paymentId: 'payment_id',
            reason: 'reason',
            amount: 'amount',
            bankName: 'bank_name',
            accountNumber: 'account_number',
            accountName: 'account_name',
            refundedAt: 'refunded_at',
        });
    });

    it.each([Product, SalesOrder, SalesOrderDetail, Payment, Refund])('hides soft-deleted %s rows by default', (model) => {
        expect(model.options.defaultScope).toEqual({ where: { isDeleted: false } });
    });

    it('relates the tables as the order service entities do', () => {
        expect(SalesOrder.associations.details.target).toBe(SalesOrderDetail);
        expect(SalesOrder.associations.details.foreignKey).toBe('salesOrderId');
        expect(SalesOrderDetail.associations.salesOrder.target).toBe(SalesOrder);
        expect(SalesOrderDetail.associations.product.target).toBe(Product);
        expect(SalesOrderDetail.associations.product.foreignKey).toBe('productId');
        expect(Payment.associations.salesOrder.target).toBe(SalesOrder);
        expect(Refund.associations.salesOrder.target).toBe(SalesOrder);
        expect(Refund.associations.payment.target).toBe(Payment);
        expect(Refund.associations.payment.foreignKey).toBe('paymentId');
    });
});
