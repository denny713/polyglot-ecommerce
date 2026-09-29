require('dotenv').config();
const express = require('express');
const { brokerConnect } = require('./configuration/broker');
const { startEventConsumer } = require('./consumer/event');
require('./configuration/mailer');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 7160;

const startServer = async () => {
    await brokerConnect();

    await startEventConsumer();

    app.listen(PORT, () => {
        console.log(`Notification Service is running on port ${PORT}`);
    });
};

startServer();