import express from 'express';
import { verifyWebhook, handleWebhookMessage } from '../controllers/webhookController.js';

const router = express.Router();

// Meta webhook verification
router.get('/', verifyWebhook);

// Meta webhook message handling
router.post('/', handleWebhookMessage);

export default router;
