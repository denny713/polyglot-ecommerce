const { DataTypes } = require('sequelize');

// Identity, auditing and soft-delete columns every table of the order service
// carries (see Base.java there).
const baseAttributes = {
    id: { type: DataTypes.BIGINT, primaryKey: true },
    isActive: DataTypes.BOOLEAN,
    isDeleted: DataTypes.BOOLEAN,
    createdBy: DataTypes.UUID,
    updatedBy: DataTypes.UUID,
    createdAt: DataTypes.DATE,
    updatedAt: DataTypes.DATE,
};

// A soft-deleted row is gone as far as any email is concerned, unless a query
// asks for it with unscoped().
const baseOptions = {
    defaultScope: { where: { isDeleted: false } },
};

module.exports = { baseAttributes, baseOptions };
