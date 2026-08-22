const express = require('express');
const router = express.Router();
const { validateCallFollowup } = require('../middleware/validation');
const { authenticateRequest } = require('../middleware/auth');
const whatsappService = require('../services/whatsappService');
const conversationService = require('../services/conversationService');

router.post('/call-followup', authenticateRequest, validateCallFollowup, async (req, res) => {
  try {
    const { callerNumber, currentStatus, callTimestamp } = req.body;

    console.log(`[${new Date().toISOString()}] Call followup received:`, {
      callerNumber,
      currentStatus,
      callTimestamp
    });

    // Normalize phone number
    const normalizedNumber = normalizePhoneNumber(callerNumber);

    // Create or update conversation record
    const conversation = await conversationService.initializeConversation({
      callerNumber: normalizedNumber,
      currentStatus,
      callTimestamp,
      initialTemplateStatus: 'pending'
    });

    // Send WhatsApp template message
    const templateResponse = await whatsappService.sendInitialTemplate(
      normalizedNumber,
      currentStatus,
      conversation.id
    );

    if (templateResponse.success) {
      // Update conversation with template sent status
      await conversationService.updateConversation(conversation.id, {
        initialTemplateStatus: 'sent',
        templateMessageId: templateResponse.messageId,
        templateSentTime: new Date().toISOString()
      });

      res.json({
        success: true,
        message: 'WhatsApp template sent successfully',
        conversationId: conversation.id,
        templateMessageId: templateResponse.messageId
      });
    } else {
      throw new Error(`Failed to send WhatsApp template: ${templateResponse.error}`);
    }
  } catch (error) {
    console.error(`[${new Date().toISOString()}] Error in call-followup:`, error.message);
    res.status(500).json({
      success: false,
      error: error.message
    });
  }
});

router.get('/status/:callerNumber', async (req, res) => {
  try {
    const { callerNumber } = req.params;
    const normalizedNumber = normalizePhoneNumber(callerNumber);

    const conversation = await conversationService.getConversation(normalizedNumber);

    if (!conversation) {
      return res.status(404).json({
        error: 'No conversation found for this number'
      });
    }

    res.json({
      callerNumber: normalizedNumber,
      conversation
    });
  } catch (error) {
    console.error(`[${new Date().toISOString()}] Error fetching status:`, error.message);
    res.status(500).json({ error: error.message });
  }
});

function normalizePhoneNumber(number) {
  // Remove all non-digit characters
  let normalized = number.replace(/\D/g, '');

  // If number doesn't start with country code, assume +91 (India)
  if (normalized.length === 10) {
    normalized = '91' + normalized;
  }

  return normalized;
}

module.exports = router;
