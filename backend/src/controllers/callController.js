import { sendInitialWhatsAppTemplate } from '../services/whatsappService.js';
import { storeCallEvent } from '../services/contactEventService.js';
import { normalizePhoneNumber } from '../utils/phoneNormalizer.js';

export async function handleCallFollowup(req, res, next) {
  try {
    const { callerNumber, currentStatus } = req.body;

    // Normalize phone number
    const normalizedNumber = normalizePhoneNumber(callerNumber);

    console.log(`📞 Processing call from ${normalizedNumber}, status: ${currentStatus}`);

    // Store call event in database
    const event = await storeCallEvent({
      callerNumber: normalizedNumber,
      status: currentStatus,
      type: 'incoming_call',
      deviceInitiated: true
    });

    console.log(`✅ Event stored with ID: ${event.id}`);

    // Send initial WhatsApp template
    const whatsappResult = await sendInitialWhatsAppTemplate(
      normalizedNumber,
      currentStatus
    );

    console.log(`💬 WhatsApp template sent: ${whatsappResult.messageId}`);

    // Return response to Android
    res.status(200).json({
      success: true,
      eventId: event.id,
      whatsappMessageId: whatsappResult.messageId,
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    console.error('Error in handleCallFollowup:', error);
    next(error);
  }
}
