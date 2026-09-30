const { getChannel } = require('../configuration/broker');
const { handleAccountEvent } = require('../handler/account');
const { handleOrderEvent } = require('../handler/order');
const { startCartConsumer } = require('./cart');
const { shouldRetry } = require('./retry');

// One message at a time per queue, acked once its email is handed to SMTP.
const consumeQueue = async (channel, queueName, handle) => {
    await channel.consume(queueName, async (msg) => {
        if (msg === null) return;

        let event;
        try {
            event = JSON.parse(msg.content.toString());
            await handle(event);
            channel.ack(msg);
        } catch (error) {
            const retry = shouldRetry(error, msg);

            console.error(
                `Failed to process ${event?.eventType ?? 'unparseable'} event ${event?.eventId ?? ''}`
                + ` (${retry ? 'requeued' : 'dropped'}):`,
                error.message,
            );

            channel.nack(msg, false, retry);
        }
    });

    console.log(`Waiting for messages in queue: ${queueName}...`);
};

const startEventConsumer = async () => {
    const channel = getChannel();
    const exchange = process.env.NOTIFICATION_EXCHANGE;

    const queues = [
        {
            queueName: process.env.ACCOUNT_QUEUE,
            routingKey: process.env.ACCOUNT_ROUTING_KEY,
            handle: handleAccountEvent,
        },
        {
            queueName: process.env.ORDER_QUEUE,
            routingKey: process.env.ORDER_ROUTING_KEY,
            handle: handleOrderEvent,
        },
    ];

    await channel.assertExchange(exchange, 'direct', { durable: true });

    // Applies to each consumer started after it on this channel.
    await channel.prefetch(1);
    for (const { queueName, routingKey, handle } of queues) {
        await channel.assertQueue(queueName, { durable: true });
        await channel.bindQueue(queueName, exchange, routingKey);
        await consumeQueue(channel, queueName, handle);
    }

    await startCartConsumer(channel, exchange);
};

module.exports = { startEventConsumer };
