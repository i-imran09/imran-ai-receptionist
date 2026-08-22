const express = require('express');
const dotenv = require('dotenv');
const callRoutes = require('./routes/callRoutes');
const webhookRoutes = require('./routes/webhookRoutes');
const errorHandler = require('./middleware/errorHandler');

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

// Middleware
app.use(express.json());

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// Routes
app.use('/api', callRoutes);
app.use('/webhook', webhookRoutes);

// 404 handler
app.use((req, res) => {
  res.status(404).json({
    error: 'Endpoint not found',
    path: req.path,
    method: req.method
  });
});

// Error handler
app.use(errorHandler);

// Start server
app.listen(PORT, () => {
  console.log(`[${new Date().toISOString()}] Imran AI Receptionist Backend started on port ${PORT}`);
  console.log(`Health check: http://localhost:${PORT}/health`);
  console.log(`Environment: ${process.env.NODE_ENV || 'development'}`);
});

module.exports = app;
