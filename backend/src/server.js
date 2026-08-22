import express from 'express';
import dotenv from 'dotenv';
import { fileURLToPath } from 'url';
import { dirname } from 'path';

import healthRouter from './routes/health.js';
import callFollowupRouter from './routes/callFollowup.js';
import webhookRouter from './routes/webhook.js';
import errorHandler from './middleware/errorHandler.js';
import { validateEnvironment } from './config/env.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3000;

// Validate environment at startup
try {
  validateEnvironment();
  console.log('✅ Environment validation passed');
} catch (error) {
  console.error('❌ Environment validation failed:', error.message);
  process.exit(1);
}

// Middleware
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Request logging middleware
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// Routes
app.use('/health', healthRouter);
app.use('/call-followup', callFollowupRouter);
app.use('/webhook', webhookRouter);

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// Error handler (must be last)
app.use(errorHandler);

// Start server
app.listen(PORT, () => {
  console.log(`🚀 Imran AI Receptionist Backend running on port ${PORT}`);
  console.log(`📍 Health check: http://localhost:${PORT}/health`);
});

export default app;
