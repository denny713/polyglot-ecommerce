jest.mock('amqplib', () => ({ connect: jest.fn() }));

const amqp = require('amqplib');

describe('broker configuration', () => {
    let broker;
    let connection;
    let channel;
    let exitSpy;
    let logSpy;
    let errorSpy;

    beforeEach(() => {
        jest.isolateModules(() => {
            broker = require('../../src/configuration/broker');
        });

        channel = { name: 'channel' };
        connection = {
            createChannel: jest.fn().mockResolvedValue(channel),
            on: jest.fn(),
        };
        amqp.connect.mockResolvedValue(connection);
        process.env.BROKER_URL = 'amqp://localhost';

        exitSpy = jest.spyOn(process, 'exit').mockImplementation(() => {});
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        exitSpy.mockRestore();
        logSpy.mockRestore();
        errorSpy.mockRestore();
    });

    const handlerFor = (eventName) => connection.on.mock.calls.find(([name]) => name === eventName)[1];

    it('throws when the channel is requested before connecting', () => {
        expect(() => broker.getChannel()).toThrow('Broker channel not initialized');
    });

    it('connects to BROKER_URL and exposes the channel', async () => {
        await broker.brokerConnect();

        expect(amqp.connect).toHaveBeenCalledWith('amqp://localhost');
        expect(connection.createChannel).toHaveBeenCalled();
        expect(broker.getChannel()).toBe(channel);
        expect(logSpy).toHaveBeenCalledWith('Connected to broker successfully');
        expect(exitSpy).not.toHaveBeenCalled();
    });

    it('logs connection errors without exiting', async () => {
        await broker.brokerConnect();

        const error = new Error('heartbeat timeout');
        handlerFor('error')(error);

        expect(errorSpy).toHaveBeenCalledWith('Broker connection error:', error);
        expect(exitSpy).not.toHaveBeenCalled();
    });

    it('exits when the connection closes', async () => {
        await broker.brokerConnect();

        handlerFor('close')();

        expect(errorSpy).toHaveBeenCalledWith('Broker connection closed, shutting down');
        expect(exitSpy).toHaveBeenCalledWith(1);
    });

    it('exits when it cannot connect', async () => {
        const error = new Error('ECONNREFUSED');
        amqp.connect.mockRejectedValue(error);

        await broker.brokerConnect();

        expect(errorSpy).toHaveBeenCalledWith('Failed to connect to broker:', error);
        expect(exitSpy).toHaveBeenCalledWith(1);
        expect(() => broker.getChannel()).toThrow('Broker channel not initialized');
    });

    it('exits when it cannot open a channel', async () => {
        connection.createChannel.mockRejectedValue(new Error('channel error'));

        await broker.brokerConnect();

        expect(exitSpy).toHaveBeenCalledWith(1);
    });
});
