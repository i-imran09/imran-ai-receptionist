import { normalizePhoneNumber } from '../utils/phoneUtils.js';
import { sendWhatsAppTemplate } from '../services/whatsappService.js';
import { storeConversation, getConversation } from '../storage/conversationStorage.js';

export const processCallFollowup = async (req, res) => {
  try {
    const { callerNumber, currentStatus, callTimestamp } = req.body;
    const normalized = normalizePhoneNumber(callerNumber);

    console.log(`[CallFollowup] Processing: ${normalized}, Status: ${currentStatus}`);

    // Get or create conversation
    let conversation = await getConversation(normalized);
    if (!conversation) {
      conversation = {
        id: `conv_${normalized}_${Date.now()}`,
        callerNumber: normalized,
        currentStatus,
        callTimestamp,
        messages: [],
        repeatCount: 1,
        firstCallTime: callTimestamp,
        lastCallTime: callTimestamp,
        templateSent: false,
        createdAt: new Date().toISOString()
      };
    } else {
      // Update repeat count
      conversation.repeatCount = (conversation.repeatCount || 1) + 1;
      conversation.lastCallTime = callTimestamp;
      conversation.currentStatus = currentStatus;
    }

    // Send WhatsApp template
    const templateResult = await sendWhatsAppTemplate(normalized, currentStatus);

    if (templateResult.success) {
      conversation.templateSent = true;
      conversation.templateMessageId = templateResult.messageId;
      conversation.templateSentTime = new Date().toISOString();
      
      await storeConversation(conversation);

      res.json({
        success: true,
        conversationId: conversation.id,
        messageId: templateResult.messageId
      });
    } else {
      throw new Error(templateResult.error);
    }
  } catch (error) {
    console.error('[CallFollowup Error]', error);
    res.status(500).json({ error: error.message });
  }
};
