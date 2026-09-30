const { DataTypes } = require('sequelize');
const { baseAttributes, baseOptions } = require('./base');

module.exports = (sequelize) => sequelize.define('SalesOrderDetail', {
    ...baseAttributes,
    salesOrderId: DataTypes.BIGINT,
    productId: DataTypes.BIGINT,
    quantity: DataTypes.INTEGER,
    unitPrice: DataTypes.DECIMAL(10, 2),
    subtotal: DataTypes.DECIMAL(12, 2),
}, { ...baseOptions, tableName: 'sales_order_detail' });
