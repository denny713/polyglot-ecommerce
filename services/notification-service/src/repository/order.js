const { Op } = require('sequelize');
const {
    Product, SalesOrder, SalesOrderDetail, Payment, Refund,
} = require('../model');

// Read-only queries over the tables the order service owns. BIGINT and NUMERIC
// columns come back from pg as strings: ids are turned into numbers, money is
// kept as a string so the templates format it without going through a float.

const toItem = (detail) => ({
    productName: detail.product?.name,
    quantity: detail.quantity,
    unitPrice: detail.unitPrice,
    subtotal: detail.subtotal,
});

const toOrder = (order) => ({
    id: Number(order.id),
    documentNumber: order.documentNumber,
    status: order.status,
    grandTotal: order.grandTotal,
    paid: order.paid,
    outstanding: order.outstanding,
    createdBy: order.createdBy,
    createdAt: order.createdAt,
    updatedAt: order.updatedAt,
    items: (order.details || []).map(toItem),
});

const toPayment = (payment) => ({
    id: Number(payment.id),
    documentNumber: payment.documentNumber,
    salesOrderId: Number(payment.salesOrderId),
    reference: payment.reference,
    method: payment.method,
    bankName: payment.bankName,
    accountNumber: payment.accountNumber,
    accountName: payment.accountName,
    amount: payment.amount,
    appliedAmount: payment.appliedAmount,
    excessAmount: payment.excessAmount,
    paidAt: payment.paidAt,
});

const toRefund = (refund) => ({
    id: Number(refund.id),
    documentNumber: refund.documentNumber,
    salesOrderId: Number(refund.salesOrderId),
    paymentId: Number(refund.paymentId),
    reason: refund.reason,
    amount: refund.amount,
    bankName: refund.bankName,
    accountNumber: refund.accountNumber,
    accountName: refund.accountName,
    refundedAt: refund.refundedAt,
});

const toProduct = (product) => ({
    id: Number(product.id),
    name: product.name,
    sellPrice: product.sellPrice,
});

// A sales order with its lines. A product taken off sale since keeps its name
// on the order, so the product is read unscoped, deleted or not. The lines are
// not required, so an order is found even if it somehow has none left.
const findSalesOrder = async (id) => {
    const order = await SalesOrder.findByPk(id, {
        include: [{
            model: SalesOrderDetail,
            as: 'details',
            required: false,
            include: [{ model: Product.unscoped(), as: 'product' }],
        }],
        order: [[{ model: SalesOrderDetail, as: 'details' }, 'id', 'ASC']],
    });
    return order ? toOrder(order) : null;
};

const findPayment = async (id) => {
    const payment = await Payment.findByPk(id);
    return payment ? toPayment(payment) : null;
};

const findRefund = async (id) => {
    const refund = await Refund.findByPk(id);
    return refund ? toRefund(refund) : null;
};

// The refunds an order made for one reason, oldest first.
const findRefundsOfOrder = async (salesOrderId, reason) => {
    const refunds = await Refund.findAll({
        where: { salesOrderId, reason },
        order: [['id', 'ASC']],
    });
    return refunds.map(toRefund);
};

// The products that still exist among these ids; a deleted one is left out.
const findProducts = async (ids) => {
    const products = await Product.findAll({
        where: { id: { [Op.in]: ids } },
        order: [['name', 'ASC']],
    });
    return products.map(toProduct);
};

module.exports = { findSalesOrder, findPayment, findRefund, findRefundsOfOrder, findProducts };
