import { generateTanglishResponse } from './groqService.js';
import { sendTextMessage } from './whatsappService.js';
import { getOrCreateConversation, storeMessage } from './contactEventService.js';

export async function processIncomingMessage(whatsappMessage) {
  try {
    const senderNumber = whatsappMessage.from;
    const messageId = whatsappMessage.id;
    let messageText = '';

    // Extract message text based on type
    if (whatsappMessage.type === 'text') {
      messageText = whatsappMessage.text.body;
    } else {
      console.log(`Received non-text message type: ${whatsappMessage.type}`);
      // For now, only handle text
      return;
    }

    console.log(`📝 Processing message from ${senderNumber}: "${messageText}"`);

    // Get or create conversation
    const conversation = await getOrCreateConversation(senderNumber);

    // Store incoming message
    await storeMessage(conversation.id, {
      direction: 'incoming',
      content: messageText,
      whatsappMessageId: messageId
    });

    // Get conversation history for context
    const history = conversation.messages || [];
    const conversationMessages = history.slice(-5).map(msg => ({
      role: msg.direction === 'incoming' ? 'user' : 'assistant',
      content: msg.content
    }));

    // Generate AI response
    const aiResponse = await generateTanglishResponse(
      messageText,
      conversation.lastStatus || 'Work',
      conversationMessages
    );

    // Send response via WhatsApp
    const sentMessage = await sendTextMessage(senderNumber, aiResponse.text);

    // Store outgoing message
    await storeMessage(conversation.id, {
      direction: 'outgoing',
      content: aiResponse.text,
      whatsappMessageId: sentMessage.messageId,
      groqGenerated: true
    });

    console.log(`✅ Response sent to ${senderNumber}`);
  } catch (error) {
    console.error('Error processing incoming message:', error);
    // Don't throw - already acknowledged webhook
  }
}
