import crypto from 'crypto';
import { processIncomingMessage } from '../services/conversationService.js';
import { storeWebhookEvent } from '../services/contactEventService.js';

export async function handleWebhookGet(req, res) {
  const verifyToken = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  if (verifyToken === process.env.META_VERIFY_TOKEN) {
    console.log('✅ Webhook verified by Meta');
    res.status(200).send(challenge);
  } else {
    console.error('❌ Invalid webhook verify token');
    res.status(403).json({ error: 'Invalid verify token' });
  }
}

export async function handleWebhookPost(req, res, next) {
  try {
    // Webhook is already verified by verifyMetaWebhook middleware
    const body = req.body;

    // Meta sends data in entry[].messaging[] or entry[].changes[]
    if (body.object !== 'whatsapp_business_account') {
      return res.status(400).json({ error: 'Invalid object type' });
    }

    // Process each entry
    if (body.entry && Array.isArray(body.entry)) {
      for (const entry of body.entry) {
        // Check for status updates (delivery, read, etc.)
        if (entry.changes && Array.isArray(entry.changes)) {
          for (const change of entry.changes) {
            const value = change.value;

            // Process incoming messages
            if (value.messages && Array.isArray(value.messages)) {
              for (const message of value.messages) {
                console.log(`📨 Incoming message from ${message.from}`);

                // Store webhook event
                await storeWebhookEvent({
                  messageId: message.id,
                  senderNumber: message.from,
                  messageType: message.type,
                  timestamp: new Date(parseInt(message.timestamp) * 1000)
                });

                // Process and respond to message
                await processIncomingMessage(message);
              }
            }

            // Handle delivery/status updates
            if (value.statuses && Array.isArray(value.statuses)) {
              for (const status of value.statuses) {
                console.log(`📦 Message status: ${status.status} for ${status.id}`);
              }
            }
          }
        }
      }
    }

    // Always respond 200 to acknowledge
    res.status(200).json({ received: true });
  } catch (error) {
    console.error('Error in handleWebhookPost:', error);
    // Still return 200 to prevent Meta resending
    res.status(200).json({ received: true, error: error.message });
  }
}
