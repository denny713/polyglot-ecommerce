const { DataTypes } = require('sequelize');
const { baseAttributes, baseOptions } = require('./base');

module.exports = (sequelize) => sequelize.define('Payment', {
    ...baseAttributes,
    documentNumber: DataTypes.STRING,
    salesOrderId: DataTypes.BIGINT,
    reference: DataTypes.STRING,
    method: DataTypes.STRING,
    bankName: DataTypes.STRING,
    accountNumber: DataTypes.STRING,
    accountName: DataTypes.STRING,
    amount: DataTypes.DECIMAL(12, 2),
    appliedAmount: DataTypes.DECIMAL(12, 2),
    excessAmount: DataTypes.DECIMAL(12, 2),
    paidAt: DataTypes.DATE,
}, { ...baseOptions, tableName: 'payment' });
