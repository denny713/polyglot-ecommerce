const { DataTypes } = require('sequelize');
const { baseAttributes, baseOptions } = require('./base');

module.exports = (sequelize) => sequelize.define('Product', {
    ...baseAttributes,
    name: DataTypes.STRING,
    description: DataTypes.TEXT,
    sellPrice: DataTypes.DECIMAL(10, 2),
    imageUrl: DataTypes.TEXT,
}, { ...baseOptions, tableName: 'product' });
