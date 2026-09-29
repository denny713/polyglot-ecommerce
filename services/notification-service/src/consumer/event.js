const { getChannel } = require('../configuration/broker');
const { handleAccountEvent, UnprocessableEventError } = require('../handler/account');

const startEventConsumer = async () => {
    const channel = getChannel();
    const exchange = process.env.NOTIFICATION_EXCHANGE;
    const queueName = process.env.ACCOUNT_QUEUE;
    const routingKey = process.env.ACCOUNT_ROUTING_KEY;

    await channel.assertExchange(exchange, 'direct', { durable: true });
    await channel.assertQueue(queueName, { durable: true });
    await channel.bindQueue(queueName, exchange, routingKey);
    await channel.prefetch(1);

    console.log(`Waiting for messages in queue: ${queueName}...`);

    channel.consume(queueName, async (msg) => {
        if (msg === null) return;

        let event;
        try {
            event = JSON.parse(msg.content.toString());
            await handleAccountEvent(event);
            channel.ack(msg);
        } catch (error) {
            const retry = !(error instanceof SyntaxError || error instanceof UnprocessableEventError)
                && !msg.fields.redelivered;

            console.error(
                `Failed to process ${event?.eventType ?? 'unparseable'} event ${event?.eventId ?? ''}`
                + ` (${retry ? 'requeued' : 'dropped'}):`,
                error.message,
            );

            channel.nack(msg, false, retry);
        }
    });
};

module.exports = { startEventConsumer };
