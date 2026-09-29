require('dotenv').config();
const express = require('express');

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 7160;

app.listen(PORT, () => {
    console.log(`Service is running on port ${PORT}`);
});