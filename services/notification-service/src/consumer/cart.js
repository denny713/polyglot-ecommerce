const { handleCartExpired } = require('../handler/cart');
const { shouldRetry } = require('./retry');

// Each cart line expires on its own, so a cart of five products arrives as five
// messages. They are held, unacked, for a window after the first one of a
// customer, then sent as one email and acked together. Holding them unacked is
// what makes this safe: if the process dies mid-window, the broker delivers them
// again. The window must stay well below RabbitMQ's consumer ack timeout (30
// minutes by default), and the prefetch must leave room for every message held.
const DEFAULT_WINDOW_MS = 5 * 60 * 1000;
const DEFAULT_PREFETCH = 100;

const startCartConsumer = async (channel, exchange) => {
    const queueName = process.env.CART_QUEUE;
    const routingKey = process.env.CART_ROUTING_KEY;
    const windowMs = Number(process.env.CART_BATCH_WINDOW_MS) || DEFAULT_WINDOW_MS;
    const prefetch = Number(process.env.CART_PREFETCH) || DEFAULT_PREFETCH;

    // userId -> { messages, productIds }
    const batches = new Map();

    const flush = async (userId) => {
        const { messages, productIds } = batches.get(userId);
        batches.delete(userId);

        try {
            await handleCartExpired({ userId, productIds: [...productIds] });
            messages.forEach((msg) => channel.ack(msg));
        } catch (error) {
            const requeued = messages.filter((msg) => shouldRetry(error, msg));

            console.error(
                `Failed to process CART_EXPIRED batch of ${messages.length} message(s) for user ${userId}`
                + ` (${requeued.length} requeued):`,
                error.message,
            );

            messages.forEach((msg) => channel.nack(msg, false, requeued.includes(msg)));
        }
    };

    const drop = (msg, reason) => {
        console.error(`Failed to process CART_EXPIRED event (dropped): ${reason}`);
        channel.nack(msg, false, false);
    };

    await channel.assertQueue(queueName, { durable: true });
    await channel.bindQueue(queueName, exchange, routingKey);
    await channel.prefetch(prefetch);

    await channel.consume(queueName, (msg) => {
        if (msg === null) return;

        let event;
        try {
            event = JSON.parse(msg.content.toString());
        } catch (error) {
            drop(msg, error.message);
            return;
        }

        if (!event.userId || event.productId === undefined || event.productId === null) {
            drop(msg, `cart event ${event.eventId} has no user or product`);
            return;
        }

        let batch = batches.get(event.userId);
        if (!batch) {
            batch = { messages: [], productIds: new Set() };
            batches.set(event.userId, batch);
            setTimeout(() => flush(event.userId), windowMs);
        }

        batch.messages.push(msg);
        batch.productIds.add(event.productId);
    });

    console.log(`Waiting for messages in queue: ${queueName} (batched every ${windowMs} ms)...`);
};

module.exports = { startCartConsumer };
