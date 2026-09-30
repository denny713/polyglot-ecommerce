const { Sequelize } = require('sequelize');

// The ecommerce database the order service writes to. This service only reads
// it, to fill in the details behind the ids an order event carries: the schema
// belongs to the Liquibase migrations, so nothing here may ever call sync().
const sequelize = new Sequelize(process.env.DB_NAME, process.env.DB_USER, process.env.DB_PASSWORD, {
    host: process.env.DB_HOST,
    port: Number(process.env.DB_PORT) || 5432,
    dialect: 'postgres',
    logging: false,
    define: {
        // Columns are snake_case, and every table is named after its entity
        // in the singular, as the migrations create them.
        underscored: true,
        freezeTableName: true,
        // Read only: nothing here writes created_at or updated_at.
        timestamps: false,
    },
});

const databaseConnect = async () => {
    try {
        await sequelize.authenticate();
        console.log('Connected to database successfully');
    } catch (error) {
        console.error('Failed to connect to database:', error);
        process.exit(1);
    }
};

module.exports = { sequelize, databaseConnect };
