jest.mock('sequelize', () => ({ Sequelize: jest.fn() }));

const { Sequelize } = require('sequelize');

describe('database configuration', () => {
    const originalEnv = process.env;
    let sequelize;
    let exitSpy;
    let logSpy;
    let errorSpy;

    const loadDatabase = () => {
        let database;
        jest.isolateModules(() => {
            database = require('../../src/configuration/database');
        });
        return database;
    };

    beforeEach(() => {
        process.env = {
            ...originalEnv,
            DB_HOST: 'localhost',
            DB_USER: 'postgres',
            DB_PASSWORD: 'secret',
            DB_NAME: 'ecommerce',
        };
        delete process.env.DB_PORT;

        sequelize = { authenticate: jest.fn() };
        Sequelize.mockImplementation(() => sequelize);
        exitSpy = jest.spyOn(process, 'exit').mockImplementation(() => {});
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        exitSpy.mockRestore();
        logSpy.mockRestore();
        errorSpy.mockRestore();
    });

    afterAll(() => {
        process.env = originalEnv;
    });

    it('connects to postgres from the DB_* variables, defaulting to port 5432', () => {
        expect(loadDatabase().sequelize).toBe(sequelize);
        expect(Sequelize).toHaveBeenCalledWith('ecommerce', 'postgres', 'secret', expect.objectContaining({
            host: 'localhost',
            port: 5432,
            dialect: 'postgres',
            logging: false,
        }));
    });

    it('uses DB_PORT when set', () => {
        process.env.DB_PORT = '6543';

        loadDatabase();

        expect(Sequelize.mock.calls[0][3].port).toBe(6543);
    });

    it('maps the snake_case schema and writes no timestamps of its own', () => {
        loadDatabase();

        expect(Sequelize.mock.calls[0][3].define).toEqual({
            underscored: true,
            freezeTableName: true,
            timestamps: false,
        });
    });

    it('checks the connection on start', async () => {
        sequelize.authenticate.mockResolvedValue();

        await loadDatabase().databaseConnect();

        expect(sequelize.authenticate).toHaveBeenCalled();
        expect(logSpy).toHaveBeenCalledWith('Connected to database successfully');
        expect(exitSpy).not.toHaveBeenCalled();
    });

    it('exits when the database cannot be reached', async () => {
        const error = new Error('ECONNREFUSED');
        sequelize.authenticate.mockRejectedValue(error);

        await loadDatabase().databaseConnect();

        expect(errorSpy).toHaveBeenCalledWith('Failed to connect to database:', error);
        expect(exitSpy).toHaveBeenCalledWith(1);
    });
});
