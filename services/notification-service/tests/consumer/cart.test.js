jest.mock('../../src/handler/cart', () => ({ handleCartExpired: jest.fn() }));

const { handleCartExpired } = require('../../src/handler/cart');
const { UnprocessableEventError } = require('../../src/handler/error');
const { startCartConsumer } = require('../../src/consumer/cart');

describe('startCartConsumer', () => {
    const originalEnv = process.env;
    let channel;
    let logSpy;
    let errorSpy;

    beforeEach(() => {
        jest.useFakeTimers();
        process.env = {
            ...originalEnv,
            CART_QUEUE: 'cart.queue',
            CART_ROUTING_KEY: 'cart.key',
        };
        delete process.env.CART_BATCH_WINDOW_MS;
        delete process.env.CART_PREFETCH;

        channel = {
            assertQueue: jest.fn().mockResolvedValue(),
            bindQueue: jest.fn().mockResolvedValue(),
            prefetch: jest.fn().mockResolvedValue(),
            consume: jest.fn().mockResolvedValue(),
            ack: jest.fn(),
            nack: jest.fn(),
        };
        handleCartExpired.mockResolvedValue();

        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
        errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        jest.useRealTimers();
        logSpy.mockRestore();
        errorSpy.mockRestore();
    });

    afterAll(() => {
        process.env = originalEnv;
    });

    const start = async () => {
        await startCartConsumer(channel, 'notification.exchange');
        return channel.consume.mock.calls[0][1];
    };

    const message = (content, redelivered = false) => ({
        content: Buffer.from(typeof content === 'string' ? content : JSON.stringify(content)),
        fields: { redelivered },
    });

    const cartEvent = (userId, productId) => message({ eventId: `evt-${productId}`, eventType: 'CART_EXPIRED', userId, productId });

    it('binds the cart queue and allows enough unacked messages to batch them', async () => {
        await start();

        expect(channel.assertQueue).toHaveBeenCalledWith('cart.queue', { durable: true });
        expect(channel.bindQueue).toHaveBeenCalledWith('cart.queue', 'notification.exchange', 'cart.key');
        expect(channel.prefetch).toHaveBeenCalledWith(100);
        expect(channel.consume).toHaveBeenCalledWith('cart.queue', expect.any(Function));
        expect(logSpy).toHaveBeenCalledWith('Waiting for messages in queue: cart.queue (batched every 300000 ms)...');
    });

    it('takes the window and prefetch from the environment', async () => {
        process.env.CART_BATCH_WINDOW_MS = '1000';
        process.env.CART_PREFETCH = '20';
        const consume = await start();

        consume(cartEvent('user-1', 7));
        await jest.advanceTimersByTimeAsync(1000);

        expect(channel.prefetch).toHaveBeenCalledWith(20);
        expect(handleCartExpired).toHaveBeenCalledTimes(1);
    });

    it('sends one email per customer for the lines that expired inside the window', async () => {
        const consume = await start();
        const first = cartEvent('user-1', 7);
        const second = cartEvent('user-1', 8);
        const again = cartEvent('user-1', 7);
        const other = cartEvent('user-2', 9);

        consume(first);
        await jest.advanceTimersByTimeAsync(60 * 1000);
        consume(second);
        consume(again);
        consume(other);

        // Nothing is acked while the window is open.
        expect(handleCartExpired).not.toHaveBeenCalled();
        expect(channel.ack).not.toHaveBeenCalled();

        await jest.advanceTimersByTimeAsync(240 * 1000);

        expect(handleCartExpired).toHaveBeenCalledTimes(1);
        expect(handleCartExpired).toHaveBeenCalledWith({ userId: 'user-1', productIds: [7, 8] });
        expect(channel.ack).toHaveBeenCalledWith(first);
        expect(channel.ack).toHaveBeenCalledWith(second);
        expect(channel.ack).toHaveBeenCalledWith(again);
        expect(channel.ack).not.toHaveBeenCalledWith(other);

        // user-2's window opened a minute later.
        await jest.advanceTimersByTimeAsync(60 * 1000);
        expect(handleCartExpired).toHaveBeenLastCalledWith({ userId: 'user-2', productIds: [9] });
        expect(channel.ack).toHaveBeenCalledWith(other);
    });

    it('starts a new batch for a line that expires after the email went out', async () => {
        const consume = await start();

        consume(cartEvent('user-1', 7));
        await jest.advanceTimersByTimeAsync(300 * 1000);
        consume(cartEvent('user-1', 8));
        await jest.advanceTimersByTimeAsync(300 * 1000);

        expect(handleCartExpired).toHaveBeenNthCalledWith(1, { userId: 'user-1', productIds: [7] });
        expect(handleCartExpired).toHaveBeenNthCalledWith(2, { userId: 'user-1', productIds: [8] });
    });

    it('requeues a batch that failed on its first delivery', async () => {
        handleCartExpired.mockRejectedValue(new Error('SMTP down'));
        const consume = await start();
        const fresh = cartEvent('user-1', 7);
        const redelivered = cartEvent('user-1', 8);
        redelivered.fields.redelivered = true;

        consume(fresh);
        consume(redelivered);
        await jest.advanceTimersByTimeAsync(300 * 1000);

        expect(channel.ack).not.toHaveBeenCalled();
        expect(channel.nack).toHaveBeenCalledWith(fresh, false, true);
        expect(channel.nack).toHaveBeenCalledWith(redelivered, false, false);
        expect(errorSpy).toHaveBeenCalledWith(
            'Failed to process CART_EXPIRED batch of 2 message(s) for user user-1 (1 requeued):',
            'SMTP down',
        );
    });

    it('drops a batch that can never be sent', async () => {
        handleCartExpired.mockRejectedValue(new UnprocessableEventError('user gone'));
        const consume = await start();
        const msg = cartEvent('user-1', 7);

        consume(msg);
        await jest.advanceTimersByTimeAsync(300 * 1000);

        expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
    });

    it('ignores a null message (consumer cancelled)', async () => {
        const consume = await start();

        consume(null);
        await jest.advanceTimersByTimeAsync(300 * 1000);

        expect(handleCartExpired).not.toHaveBeenCalled();
        expect(channel.nack).not.toHaveBeenCalled();
    });

    it('drops an unparseable message straight away', async () => {
        const consume = await start();
        const msg = message('not json');

        consume(msg);

        expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
        expect(errorSpy.mock.calls[0][0]).toMatch(/^Failed to process CART_EXPIRED event \(dropped\): /);
    });

    it.each([
        [{ eventId: 'evt-1', productId: 7 }],
        [{ eventId: 'evt-1', userId: 'user-1' }],
        [{ eventId: 'evt-1', userId: 'user-1', productId: null }],
    ])('drops an event without a user or product: %j', async (event) => {
        const consume = await start();
        const msg = message(event);

        consume(msg);
        await jest.advanceTimersByTimeAsync(300 * 1000);

        expect(channel.nack).toHaveBeenCalledWith(msg, false, false);
        expect(errorSpy).toHaveBeenCalledWith(
            'Failed to process CART_EXPIRED event (dropped): cart event evt-1 has no user or product',
        );
        expect(handleCartExpired).not.toHaveBeenCalled();
    });

    it('accepts a product id of zero', async () => {
        const consume = await start();

        consume(cartEvent('user-1', 0));
        await jest.advanceTimersByTimeAsync(300 * 1000);

        expect(handleCartExpired).toHaveBeenCalledWith({ userId: 'user-1', productIds: [0] });
    });
});
