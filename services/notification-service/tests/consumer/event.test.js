jest.mock('../../src/configuration/broker', () => ({ getChannel: jest.fn() }));
jest.mock('../../src/handler/account', () => ({ handleAccountEvent: jest.fn() }));
jest.mock('../../src/handler/order', () => ({ handleOrderEvent: jest.fn() }));
jest.mock('../../src/consumer/cart', () => ({ startCartConsumer: jest.fn() }));

const { getChannel } = require('../../src/configuration/broker');
const { handleAccountEvent } = require('../../src/handler/account');
const { handleOrderEvent } = require('../../src/handler/order');
const { startCartConsumer } = require('../../src/consumer/cart');
const { UnprocessableEventError } = require('../../src/handler/error');
const { startEventConsumer } = require('../../src/consumer/event');

describe('startEventConsumer', () => {
    const originalEnv = process.env;
    let channel;
    let logSpy;
    let errorSpy;

    beforeEach(() => {
        process.env = {
            ...originalEnv,
            NOTIFICATION_EXCHANGE: 'notification.exchange',
            ACCOUNT_QUEUE: 'account.queue',
            ACCOUNT_ROUTING_KEY: 'account.key',
            ORDER_QUEUE: 'order.queue',
            ORDER_ROUTING_KEY: 'order.key',
        };

        channel = {
            assertExchange: jest.fn().mockResolvedValue(),
            assertQueue: jest.fn().mockResolvedValue(),
            bindQueue: jest.fn().mockResolvedValue(),
            prefetch: jest.fn().mockResolvedValue(),
            consume: jest.fn().mockResolvedValue(),
            ack: jest.fn(),
            nack: jest.fn(),
        };
        getChannel.mockReturnValue(channel);

        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        logSpy.mockRestore();
        errorSpy.mockRestore();
    });

    afterAll(() => {
        process.env = originalEnv;
    });

    const consumerOf = async (queueName) => {
        await startEventConsumer();
        return channel.consume.mock.calls.find(([name]) => name === queueName)[1];
    };

    const message = (content, redelivered = false) => ({
        content: Buffer.from(typeof content === 'string' ? content : JSON.stringify(content)),
        fields: { redelivered },
    });

    it('sets up the exchange, both queues and the cart consumer', async () => {
        await startEventConsumer();

        expect(channel.assertExchange).toHaveBeenCalledWith('notification.exchange', 'direct', { durable: true });
        expect(channel.prefetch).toHaveBeenCalledWith(1);
        expect(channel.assertQueue).toHaveBeenCalledWith('account.queue', { durable: true });
        expect(channel.bindQueue).toHaveBeenCalledWith('account.queue', 'notification.exchange', 'account.key');
        expect(channel.assertQueue).toHaveBeenCalledWith('order.queue', { durable: true });
        expect(channel.bindQueue).toHaveBeenCalledWith('order.queue', 'notification.exchange', 'order.key');
        expect(channel.consume).toHaveBeenCalledWith('account.queue', expect.any(Function));
        expect(channel.consume).toHaveBeenCalledWith('order.queue', expect.any(Function));
        expect(logSpy).toHaveBeenCalledWith('Waiting for messages in queue: account.queue...');
        expect(logSpy).toHaveBeenCalledWith('Waiting for messages in queue: order.queue...');
        expect(startCartConsumer).toHaveBeenCalledWith(channel, 'notification.exchange');
    });

    it('limits both queues to one message at a time before consuming', async () => {
        await startEventConsumer();

        const prefetchOrder = channel.prefetch.mock.invocationCallOrder[0];
        channel.consume.mock.invocationCallOrder.forEach((order) => expect(order).toBeGreaterThan(prefetchOrder));
    });

    it('fails when the broker channel is not ready', async () => {
        getChannel.mockImplementation(() => {
            throw new Error('Broker channel not initialized');
        });

        await expect(startEventConsumer()).rejects.toThrow('Broker channel not initialized');
    });

    describe.each([
        ['account.queue', handleAccountEvent, { eventId: 'evt-1', eventType: 'ACCOUNT_REGISTERED' }],
        ['order.queue', handleOrderEvent, { eventId: 'evt-1', eventType: 'PAYMENT_SUCCEEDED', id: 41 }],
    ])('messages on %s', (queueName, handler, event) => {
        it('ignores a null message (consumer cancelled)', async () => {
            const consume = await consumerOf(queueName);

            await consume(null);

            expect(handler).not.toHaveBeenCalled();
            expect(channel.ack).not.toHaveBeenCalled();
            expect(channel.nack).not.toHaveBeenCalled();
        });

        it('handles and acks a valid event', async () => {
            const consume = await consumerOf(queueName);
            const msg = message(event);

            await consume(msg);

            expect(handler).toHaveBeenCalledWith(event);
            expect(channel.ack).toHaveBeenCalledWith(msg);
            expect(channel.nack).not.toHaveBeenCalled();
        });

        it('drops an unparseable message', async () => {
            const consume = await consumerOf(queueName);
            const msg = message('not json');

            await consume(msg);

            expect(handler).not.toHaveBeenCalled();
            expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
            expect(errorSpy.mock.calls[0][0]).toBe('Failed to process unparseable event  (dropped):');
        });

        it('drops an unprocessable event', async () => {
            handler.mockRejectedValue(new UnprocessableEventError('not found'));
            const consume = await consumerOf(queueName);
            const msg = message(event);

            await consume(msg);

            expect(channel.ack).not.toHaveBeenCalled();
            expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
            expect(errorSpy).toHaveBeenCalledWith(`Failed to process ${event.eventType} event evt-1 (dropped):`, 'not found');
        });

        it('requeues a transient failure on first delivery', async () => {
            handler.mockRejectedValue(new Error('SMTP down'));
            const consume = await consumerOf(queueName);
            const msg = message(event);

            await consume(msg);

            expect(channel.nack).toHaveBeenCalledWith(msg, false, true);
            expect(errorSpy).toHaveBeenCalledWith(`Failed to process ${event.eventType} event evt-1 (requeued):`, 'SMTP down');
        });

        it('drops a transient failure that was already redelivered', async () => {
            handler.mockRejectedValue(new Error('SMTP down'));
            const consume = await consumerOf(queueName);
            const msg = message(event, true);

            await consume(msg);

            expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
        });

        it('logs an empty id for an event without eventType/eventId', async () => {
            handler.mockRejectedValue(new Error('boom'));
            const consume = await consumerOf(queueName);

            await consume(message({}));

            expect(errorSpy.mock.calls[0][0]).toBe('Failed to process unparseable event  (requeued):');
        });
    });
});
