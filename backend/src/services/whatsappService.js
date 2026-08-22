const axios = require('axios');

const META_GRAPH_API_VERSION = process.env.META_GRAPH_API_VERSION || 'v18.0';
const META_ACCESS_TOKEN = process.env.META_ACCESS_TOKEN;
const META_PHONE_NUMBER_ID = process.env.META_PHONE_NUMBER_ID;

const BASE_URL = `https://graph.instagram.com/${META_GRAPH_API_VERSION}/${META_PHONE_NUMBER_ID}/messages`;

async function sendInitialTemplate(recipientNumber, currentStatus, conversationId) {
  if (!META_ACCESS_TOKEN || !META_PHONE_NUMBER_ID) {
    console.warn('[WhatsApp] Meta credentials not configured, skipping template send');
    return {
      success: false,
      error: 'Meta credentials not configured'
    };
  }

  try {
    const templateName = process.env.META_INITIAL_TEMPLATE || 'call_followup';
    const templateLanguage = process.env.META_TEMPLATE_LANGUAGE || 'en';

    const payload = {
      messaging_product: 'whatsapp',
      recipient_type: 'individual',
      to: recipientNumber,
      type: 'template',
      template: {
        name: templateName,
        language: {
          code: templateLanguage
        },
        body: {
          parameters: [
            {
              type: 'text',
              text: currentStatus
            }
          ]
        }
      }
    };

    console.log(`[${new Date().toISOString()}] Sending WhatsApp template:`, {
      to: recipientNumber,
      template: templateName,
      currentStatus,
      conversationId
    });

    const response = await axios.post(BASE_URL, payload, {
      headers: {
        'Authorization': `Bearer ${META_ACCESS_TOKEN}`,
        'Content-Type': 'application/json'
      },
      timeout: 15000
    });

    if (response.data && response.data.messages && response.data.messages[0]) {
      const messageId = response.data.messages[0].id;
      console.log(`[${new Date().toISOString()}] Template sent successfully:`, { messageId });
      return {
        success: true,
        messageId
      };
    } else {
      throw new Error('Unexpected Meta API response format');
    }
  } catch (error) {
    if (error.response) {
      console.error(`[${new Date().toISOString()}] Meta API error:`, {
        status: error.response.status,
        error: error.response.data?.error?.message || 'Unknown error'
      });
      return {
        success: false,
        error: error.response.data?.error?.message || 'Meta API error'
      };
    } else {
      console.error(`[${new Date().toISOString()}] Error sending template:`, error.message);
      return {
        success: false,
        error: error.message
      };
    }
  }
}

async function sendMessage(recipientNumber, messageText) {
  if (!META_ACCESS_TOKEN || !META_PHONE_NUMBER_ID) {
    console.warn('[WhatsApp] Meta credentials not configured, skipping message send');
    return {
      success: false,
      error: 'Meta credentials not configured'
    };
  }

  try {
    // Truncate message if too long (WhatsApp limit is 4096 characters)
    const text = messageText.substring(0, 4096);

    const payload = {
      messaging_product: 'whatsapp',
      recipient_type: 'individual',
      to: recipientNumber,
      type: 'text',
      text: {
        body: text
      }
    };

    console.log(`[${new Date().toISOString()}] Sending WhatsApp message:`, {
      to: recipientNumber,
      length: text.length
    });

    const response = await axios.post(BASE_URL, payload, {
      headers: {
        'Authorization': `Bearer ${META_ACCESS_TOKEN}`,
        'Content-Type': 'application/json'
      },
      timeout: 15000
    });

    if (response.data && response.data.messages && response.data.messages[0]) {
      const messageId = response.data.messages[0].id;
      console.log(`[${new Date().toISOString()}] Message sent successfully:`, { messageId });
      return {
        success: true,
        messageId
      };
    } else {
      throw new Error('Unexpected Meta API response format');
    }
  } catch (error) {
    if (error.response) {
      console.error(`[${new Date().toISOString()}] Meta API error:`, {
        status: error.response.status,
        error: error.response.data?.error?.message || 'Unknown error'
      });
      return {
        success: false,
        error: error.response.data?.error?.message || 'Meta API error'
      };
    } else {
      console.error(`[${new Date().toISOString()}] Error sending message:`, error.message);
      return {
        success: false,
        error: error.message
      };
    }
  }
}

module.exports = {
  sendInitialTemplate,
  sendMessage
};
