jest.mock('../../src/configuration/broker', () => ({ getChannel: jest.fn() }));
// The real handler is kept for UnprocessableEventError; stub its mail dependency
// so loading it never opens an SMTP connection.
jest.mock('../../src/service/mail', () => ({ sendEmail: jest.fn() }));
jest.mock('../../src/handler/account', () => ({
    ...jest.requireActual('../../src/handler/account'),
    handleAccountEvent: jest.fn(),
}));

const { getChannel } = require('../../src/configuration/broker');
const { handleAccountEvent, UnprocessableEventError } = require('../../src/handler/account');
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
        };

        channel = {
            assertExchange: jest.fn().mockResolvedValue(),
            assertQueue: jest.fn().mockResolvedValue(),
            bindQueue: jest.fn().mockResolvedValue(),
            prefetch: jest.fn().mockResolvedValue(),
            consume: jest.fn(),
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

    const startAndGetConsumer = async () => {
        await startEventConsumer();
        return channel.consume.mock.calls[0][1];
    };

    const message = (content, redelivered = false) => ({
        content: Buffer.from(typeof content === 'string' ? content : JSON.stringify(content)),
        fields: { redelivered },
    });

    const event = { eventId: 'evt-1', eventType: 'ACCOUNT_REGISTERED', email: 'jane@example.com' };

    it('sets up the exchange, queue and binding', async () => {
        await startEventConsumer();

        expect(channel.assertExchange).toHaveBeenCalledWith('notification.exchange', 'direct', { durable: true });
        expect(channel.assertQueue).toHaveBeenCalledWith('account.queue', { durable: true });
        expect(channel.bindQueue).toHaveBeenCalledWith('account.queue', 'notification.exchange', 'account.key');
        expect(channel.prefetch).toHaveBeenCalledWith(1);
        expect(channel.consume).toHaveBeenCalledWith('account.queue', expect.any(Function));
        expect(logSpy).toHaveBeenCalledWith('Waiting for messages in queue: account.queue...');
    });

    it('fails when the broker channel is not ready', async () => {
        getChannel.mockImplementation(() => {
            throw new Error('Broker channel not initialized');
        });

        await expect(startEventConsumer()).rejects.toThrow('Broker channel not initialized');
    });

    it('ignores a null message (consumer cancelled)', async () => {
        const consume = await startAndGetConsumer();

        await consume(null);

        expect(handleAccountEvent).not.toHaveBeenCalled();
        expect(channel.ack).not.toHaveBeenCalled();
        expect(channel.nack).not.toHaveBeenCalled();
    });

    it('handles and acks a valid event', async () => {
        const consume = await startAndGetConsumer();
        const msg = message(event);

        await consume(msg);

        expect(handleAccountEvent).toHaveBeenCalledWith(event);
        expect(channel.ack).toHaveBeenCalledWith(msg);
        expect(channel.nack).not.toHaveBeenCalled();
    });

    it('drops an unparseable message', async () => {
        const consume = await startAndGetConsumer();
        const msg = message('not json');

        await consume(msg);

        expect(handleAccountEvent).not.toHaveBeenCalled();
        expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
        expect(errorSpy.mock.calls[0][0]).toBe('Failed to process unparseable event  (dropped):');
    });

    it('drops an unprocessable event', async () => {
        handleAccountEvent.mockRejectedValue(new UnprocessableEventError('Unknown account event type: X'));
        const consume = await startAndGetConsumer();
        const msg = message(event);

        await consume(msg);

        expect(channel.ack).not.toHaveBeenCalled();
        expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
        expect(errorSpy).toHaveBeenCalledWith(
            'Failed to process ACCOUNT_REGISTERED event evt-1 (dropped):',
            'Unknown account event type: X',
        );
    });

    it('requeues a transient failure on first delivery', async () => {
        handleAccountEvent.mockRejectedValue(new Error('SMTP down'));
        const consume = await startAndGetConsumer();
        const msg = message(event);

        await consume(msg);

        expect(channel.nack).toHaveBeenCalledWith(msg, false, true);
        expect(errorSpy).toHaveBeenCalledWith(
            'Failed to process ACCOUNT_REGISTERED event evt-1 (requeued):',
            'SMTP down',
        );
    });

    it('drops a transient failure that was already redelivered', async () => {
        handleAccountEvent.mockRejectedValue(new Error('SMTP down'));
        const consume = await startAndGetConsumer();
        const msg = message(event, true);

        await consume(msg);

        expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
    });

    it('logs an empty id for an event without eventType/eventId', async () => {
        handleAccountEvent.mockRejectedValue(new Error('boom'));
        const consume = await startAndGetConsumer();

        await consume(message({}));

        expect(errorSpy.mock.calls[0][0]).toBe('Failed to process unparseable event  (requeued):');
    });
});
