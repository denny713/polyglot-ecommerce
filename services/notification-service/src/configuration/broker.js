const amqp = require('amqplib');

let connection = null;
let channel = null;

const brokerConnect = async () => {
    try {
        connection = await amqp.connect(process.env.BROKER_URL);
        channel = await connection.createChannel();

        connection.on('error', (error) => console.error('Broker connection error:', error));
        connection.on('close', () => {
            console.error('Broker connection closed, shutting down');
            process.exit(1);
        });

        console.log('Connected to broker successfully');
    } catch (error) {
        console.error('Failed to connect to broker:', error);
        process.exit(1);
    }
};

const getChannel = () => {
    if (!channel) throw new Error('Broker channel not initialized');
    return channel;
};

module.exports = { brokerConnect, getChannel };
