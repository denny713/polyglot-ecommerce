const { DataTypes } = require('sequelize');
const { baseAttributes, baseOptions } = require('./base');

module.exports = (sequelize) => sequelize.define('Refund', {
    ...baseAttributes,
    documentNumber: DataTypes.STRING,
    salesOrderId: DataTypes.BIGINT,
    paymentId: DataTypes.BIGINT,
    reason: DataTypes.STRING,
    amount: DataTypes.DECIMAL(12, 2),
    bankName: DataTypes.STRING,
    accountNumber: DataTypes.STRING,
    accountName: DataTypes.STRING,
    refundedAt: DataTypes.DATE,
}, { ...baseOptions, tableName: 'refund' });
