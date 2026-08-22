const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const groqService = require('../services/groqService');
const whatsappService = require('../services/whatsappService');
const conversationService = require('../services/conversationService');
const promptService = require('../services/promptService');

const VERIFY_TOKEN = process.env.META_VERIFY_TOKEN || 'default_verify_token';

// Meta webhook verification (GET)
router.get('/', (req, res) => {
  const mode = req.query['hub.mode'];
  const token = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  if (mode && token) {
    if (mode === 'subscribe' && token === VERIFY_TOKEN) {
      console.log(`[${new Date().toISOString()}] Webhook verified successfully`);
      res.status(200).send(challenge);
    } else {
      console.warn(`[${new Date().toISOString()}] Webhook verification failed: invalid token`);
      res.sendStatus(403);
    }
  } else {
    res.sendStatus(400);
  }
});

// Meta webhook messages (POST)
router.post('/', express.raw({ type: 'application/json' }), async (req, res) => {
  try {
    // Verify webhook signature
    const signature = req.headers['x-hub-signature-256'];
    if (!verifyWebhookSignature(req.body, signature)) {
      console.warn(`[${new Date().toISOString()}] Invalid webhook signature`);
      return res.sendStatus(403);
    }

    const body = JSON.parse(req.body.toString());

    if (body.object === 'whatsapp_business_account') {
      for (const entry of body.entry) {
        for (const change of entry.changes) {
          if (change.field === 'messages') {
            await handleIncomingMessage(change.value);
          }
        }
      }
    }

    // Always respond with 200 to acknowledge receipt
    res.sendStatus(200);
  } catch (error) {
    console.error(`[${new Date().toISOString()}] Error processing webhook:`, error.message);
    res.sendStatus(500);
  }
});

async function handleIncomingMessage(messageData) {
  try {
    const messages = messageData.messages || [];
    const contacts = messageData.contacts || [];

    for (const message of messages) {
      if (message.type === 'text') {
        const callerNumber = message.from;
        const incomingText = message.text.body;
        const messageId = message.id;
        const timestamp = message.timestamp;

        console.log(`[${new Date().toISOString()}] Incoming WhatsApp message:`, {
          from: callerNumber,
          text: incomingText,
          messageId
        });

        // Get or create conversation
        let conversation = await conversationService.getConversation(callerNumber);
        if (!conversation) {
          conversation = await conversationService.initializeConversation({
            callerNumber,
            currentStatus: 'Work',
            callTimestamp: new Date(timestamp * 1000).toISOString()
          });
        }

        // Add message to conversation
        await conversationService.addMessage(conversation.id, {
          type: 'incoming',
          text: incomingText,
          timestamp: new Date(timestamp * 1000).toISOString(),
          messageId
        });

        // Get current status (retrieve from last known or default)
        const currentStatus = conversation.currentStatus || 'Work';

        // Generate AI response using Groq
        const systemPrompt = promptService.generateSystemPrompt(currentStatus, conversation.id);
        const aiResponse = await groqService.generateResponse(
          systemPrompt,
          incomingText,
          conversation.messages || []
        );

        // Add AI response to conversation
        await conversationService.addMessage(conversation.id, {
          type: 'outgoing',
          text: aiResponse,
          timestamp: new Date().toISOString(),
          source: 'groq_ai'
        });

        // Send AI response via WhatsApp
        const sendResponse = await whatsappService.sendMessage(
          callerNumber,
          aiResponse
        );

        if (sendResponse.success) {
          console.log(`[${new Date().toISOString()}] AI response sent:`, {
            to: callerNumber,
            messageId: sendResponse.messageId
          });
        } else {
          console.error(`[${new Date().toISOString()}] Failed to send response:`, sendResponse.error);
        }
      }
    }
  } catch (error) {
    console.error(`[${new Date().toISOString()}] Error handling incoming message:`, error.message);
  }
}

function verifyWebhookSignature(body, signature) {
  if (!signature) return false;

  const appSecret = process.env.META_APP_SECRET;
  if (!appSecret) {
    console.warn('META_APP_SECRET not configured, skipping signature verification');
    return true;
  }

  const hash = crypto
    .createHmac('sha256', appSecret)
    .update(body)
    .digest('hex');

  const expectedSignature = `sha256=${hash}`;
  return signature === expectedSignature;
}

module.exports = router;
