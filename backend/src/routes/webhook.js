import express from 'express';
import { handleWebhookGet, handleWebhookPost } from '../controllers/webhookController.js';
import { verifyMetaWebhook } from '../middleware/auth.js';
import { rateLimitWebhook } from '../middleware/rateLimit.js';

const router = express.Router();

// GET /webhook (Meta webhook verification)
router.get('/', handleWebhookGet);

// POST /webhook (incoming WhatsApp messages)
router.post('/', rateLimitWebhook, verifyMetaWebhook, handleWebhookPost);

export default router;
