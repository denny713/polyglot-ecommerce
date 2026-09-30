const { sequelize } = require('../configuration/database');
const defineProduct = require('./product');
const defineSalesOrder = require('./sales-order');
const defineSalesOrderDetail = require('./sales-order-detail');
const definePayment = require('./payment');
const defineRefund = require('./refund');

// Read-only models of the tables the order service owns; the columns and
// relations mirror its entities. Only what an email needs is mapped.
const Product = defineProduct(sequelize);
const SalesOrder = defineSalesOrder(sequelize);
const SalesOrderDetail = defineSalesOrderDetail(sequelize);
const Payment = definePayment(sequelize);
const Refund = defineRefund(sequelize);

SalesOrder.hasMany(SalesOrderDetail, { as: 'details', foreignKey: 'salesOrderId' });
SalesOrderDetail.belongsTo(SalesOrder, { as: 'salesOrder', foreignKey: 'salesOrderId' });
SalesOrderDetail.belongsTo(Product, { as: 'product', foreignKey: 'productId' });
Payment.belongsTo(SalesOrder, { as: 'salesOrder', foreignKey: 'salesOrderId' });
Refund.belongsTo(SalesOrder, { as: 'salesOrder', foreignKey: 'salesOrderId' });
Refund.belongsTo(Payment, { as: 'payment', foreignKey: 'paymentId' });

module.exports = {
    sequelize, Product, SalesOrder, SalesOrderDetail, Payment, Refund,
};
