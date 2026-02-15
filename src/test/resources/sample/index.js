const awsServerlessExpress = require('aws-serverless-express');
const express = require('express');

const app = express();

// Simple CORS middleware
app.use((req, res, next) => {
    res.header("Access-Control-Allow-Origin", "*");
    res.header("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    res.header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Amz-Date,X-Api-Key,X-Amz-Security-Token");
    if (req.method === "OPTIONS") return res.sendStatus(200);
    next();
});

app.get('/', (req, res) => {
    console.log('Received request for / IMHERE');
    res.send('Hello World from Lambda!');
});

const server = awsServerlessExpress.createServer(app);

exports.handler = (event, context) => {
    awsServerlessExpress.proxy(server, event, context);
};
