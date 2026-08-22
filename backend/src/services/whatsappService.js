import axios from 'axios';
import { getConfig } from '../config/env.js';

const config = getConfig();
const baseUrl = `https://graph.instagram.com/${config.meta.graphApiVersion}/${config.meta.phoneNumberId}/messages`;

export const sendWhatsAppTemplate = async (recipientNumber, currentStatus) => {
  try {
    const payload = {
      messaging_product: 'whatsapp',
      to: recipientNumber,
      type: 'template',
      template: {
        name: config.meta.initialTemplate,
        language: {
          code: config.meta.templateLanguage
        },
        body: {
          parameters: [
            { type: 'text', text: currentStatus }
          ]
        }
      }
    };

    console.log(`[WhatsApp] Sending template to ${recipientNumber}`);

    const response = await axios.post(baseUrl, payload, {
      headers: {
        'Authorization': `Bearer ${config.meta.accessToken}`,
        'Content-Type': 'application/json'
      },
      timeout: 15000
    });

    if (response.data?.messages?.[0]?.id) {
      return { success: true, messageId: response.data.messages[0].id };
    }
    return { success: false, error: 'No message ID in response' };
  } catch (error) {
    const errorMsg = error.response?.data?.error?.message || error.message;
    console.error('[WhatsApp Template Error]', errorMsg);
    return { success: false, error: errorMsg };
  }
};

export const sendWhatsAppMessage = async (recipientNumber, messageText) => {
  try {
    const text = messageText.substring(0, 4096);
    const payload = {
      messaging_product: 'whatsapp',
      to: recipientNumber,
      type: 'text',
      text: { body: text }
    };

    console.log(`[WhatsApp] Sending message to ${recipientNumber}`);

    const response = await axios.post(baseUrl, payload, {
      headers: {
        'Authorization': `Bearer ${config.meta.accessToken}`,
        'Content-Type': 'application/json'
      },
      timeout: 15000
    });

    if (response.data?.messages?.[0]?.id) {
      return { success: true, messageId: response.data.messages[0].id };
    }
    return { success: false, error: 'No message ID in response' };
  } catch (error) {
    const errorMsg = error.response?.data?.error?.message || error.message;
    console.error('[WhatsApp Message Error]', errorMsg);
    return { success: false, error: errorMsg };
  }
};
