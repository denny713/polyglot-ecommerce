const { DataTypes } = require('sequelize');
const { baseAttributes, baseOptions } = require('./base');

module.exports = (sequelize) => sequelize.define('SalesOrder', {
    ...baseAttributes,
    documentNumber: DataTypes.STRING,
    status: DataTypes.STRING,
    grandTotal: DataTypes.DECIMAL(12, 2),
    paid: DataTypes.DECIMAL(12, 2),
    outstanding: DataTypes.DECIMAL(12, 2),
}, { ...baseOptions, tableName: 'sales_order' });
