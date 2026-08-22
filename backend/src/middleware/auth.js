import crypto from 'crypto';
import { getConfig } from '../config/env.js';

const config = getConfig();

export const authMiddleware = (req, res, next) => {
  if (!config.app.sharedSecret) {
    console.warn('APP_SHARED_SECRET not configured, skipping auth');
    return next();
  }

  const authHeader = req.headers['authorization'];
  if (!authHeader) {
    return res.status(401).json({ error: 'Missing authorization header' });
  }

  const token = authHeader.replace('Bearer ', '');
  if (token !== config.app.sharedSecret) {
    return res.status(403).json({ error: 'Invalid authorization token' });
  }

  next();
};

export const verifyWebhookSignature = (payload, signature) => {
  if (!config.meta.appSecret || !signature) {
    return true;
  }

  const hash = crypto
    .createHmac('sha256', config.meta.appSecret)
    .update(payload)
    .digest('hex');

  const expectedSignature = `sha256=${hash}`;
  return signature === expectedSignature;
};
