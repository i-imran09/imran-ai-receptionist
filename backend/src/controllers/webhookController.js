import { getConfig } from '../config/env.js';
import { verifyWebhookSignature } from '../middleware/auth.js';
import { storeConversation, getConversation, addMessage } from '../storage/conversationStorage.js';
import { generateAIResponse } from '../services/groqService.js';
import { sendWhatsAppMessage } from '../services/whatsappService.js';
import { getSystemPrompt } from '../prompts/systemPrompts.js';

const config = getConfig();
const processedWebhookIds = new Set();

export const verifyWebhook = (req, res) => {
  const mode = req.query['hub.mode'];
  const token = req.query['hub.verify_token'];
  const challenge = req.query['hub.challenge'];

  if (mode === 'subscribe' && token === config.meta.verifyToken) {
    console.log('[Webhook] Verified');
    res.status(200).send(challenge);
  } else {
    console.warn('[Webhook] Verification failed');
    res.sendStatus(403);
  }
};

export const handleWebhookMessage = async (req, res) => {
  try {
    const signature = req.headers['x-hub-signature-256'];
    const rawBody = JSON.stringify(req.body);

    if (!verifyWebhookSignature(rawBody, signature)) {
      console.warn('[Webhook] Signature verification failed');
      return res.sendStatus(403);
    }

    res.sendStatus(200);

    const { entry } = req.body;
    if (!entry) return;

    for (const e of entry) {
      for (const change of e.changes || []) {
        if (change.field === 'messages') {
          await processIncomingMessage(change.value);
        }
      }
    }
  } catch (error) {
    console.error('[Webhook Error]', error);
    res.sendStatus(500);
  }
};

const processIncomingMessage = async (messageData) => {
  try {
    const messages = messageData.messages || [];

    for (const message of messages) {
      if (message.type !== 'text') continue;

      const webhookId = message.id;
      if (processedWebhookIds.has(webhookId)) {
        console.log('[Webhook] Duplicate message, skipping:', webhookId);
        continue;
      }
      processedWebhookIds.add(webhookId);

      const callerNumber = message.from;
      const incomingText = message.text.body;
      const timestamp = new Date(message.timestamp * 1000).toISOString();

      console.log(`[Webhook] Message from ${callerNumber}: ${incomingText}`);

      // Get conversation
      let conversation = await getConversation(callerNumber);
      if (!conversation) {
        console.log('[Webhook] No conversation found, creating new');
        conversation = {
          id: `conv_${callerNumber}_${Date.now()}`,
          callerNumber,
          currentStatus: 'Work',
          messages: [],
          repeatCount: 1,
          createdAt: new Date().toISOString()
        };
      }

      // Add incoming message
      const incomingMsg = {
        id: webhookId,
        type: 'incoming',
        text: incomingText,
        timestamp,
        source: 'whatsapp'
      };
      conversation.messages.push(incomingMsg);

      // Generate AI response
      const systemPrompt = getSystemPrompt(conversation.currentStatus, conversation.id);
      const recentMessages = conversation.messages.slice(-5).map(m => ({
        role: m.type === 'incoming' ? 'user' : 'assistant',
        content: m.text
      }));

      const aiResponse = await generateAIResponse(systemPrompt, incomingText, recentMessages);

      // Add outgoing message
      const outgoingMsg = {
        id: `resp_${Date.now()}`,
        type: 'outgoing',
        text: aiResponse,
        timestamp: new Date().toISOString(),
        source: 'groq'
      };
      conversation.messages.push(outgoingMsg);

      // Send AI response
      const sendResult = await sendWhatsAppMessage(callerNumber, aiResponse);
      if (sendResult.success) {
        outgoingMsg.whatsappMessageId = sendResult.messageId;
      }

      // Store conversation
      await storeConversation(conversation);
      console.log('[Webhook] Conversation saved');
    }
  } catch (error) {
    console.error('[ProcessMessage Error]', error);
  }
};
