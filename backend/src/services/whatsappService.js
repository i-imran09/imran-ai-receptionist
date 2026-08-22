import axios from 'axios';

const META_API_URL = `https://graph.instagram.com/${process.env.META_GRAPH_API_VERSION}`;

export async function sendInitialWhatsAppTemplate(
  recipientNumber,
  currentStatus
) {
  const templateName = process.env.META_INITIAL_TEMPLATE;
  const templateLanguage = process.env.META_TEMPLATE_LANGUAGE;

  console.log(
    `Sending WhatsApp template: ${templateName} to ${recipientNumber}`
  );

  try {
    const payload = {
      messaging_product: 'whatsapp',
      to: recipientNumber,
      type: 'template',
      template: {
        name: templateName,
        language: {
          code: templateLanguage
        },
        components: [
          {
            type: 'body',
            parameters: [
              {
                type: 'text',
                text: currentStatus
              }
            ]
          }
        ]
      }
    };

    const response = await axios.post(
      `${META_API_URL}/${process.env.META_PHONE_NUMBER_ID}/messages`,
      payload,
      {
        headers: {
          Authorization: `Bearer ${process.env.META_ACCESS_TOKEN}`,
          'Content-Type': 'application/json'
        }
      }
    );

    console.log('✅ WhatsApp template sent successfully');
    return {
      messageId: response.data.messages[0].id,
      status: 'sent'
    };
  } catch (error) {
    console.error('Error sending WhatsApp template:', error.response?.data || error.message);
    throw new Error(`Failed to send WhatsApp template: ${error.message}`);
  }
}

export async function sendTextMessage(recipientNumber, messageText) {
  console.log(`Sending text message to ${recipientNumber}`);

  try {
    const payload = {
      messaging_product: 'whatsapp',
      to: recipientNumber,
      type: 'text',
      text: {
        body: messageText
      }
    };

    const response = await axios.post(
      `${META_API_URL}/${process.env.META_PHONE_NUMBER_ID}/messages`,
      payload,
      {
        headers: {
          Authorization: `Bearer ${process.env.META_ACCESS_TOKEN}`,
          'Content-Type': 'application/json'
        }
      }
    );

    console.log('✅ Text message sent successfully');
    return {
      messageId: response.data.messages[0].id,
      status: 'sent'
    };
  } catch (error) {
    console.error('Error sending text message:', error.response?.data || error.message);
    throw new Error(`Failed to send text message: ${error.message}`);
  }
}
