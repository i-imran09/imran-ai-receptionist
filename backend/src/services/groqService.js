import axios from 'axios';
import { getReceptionistPrompt } from '../prompts/receptionistPrompt.js';

const GROQ_API_URL = 'https://api.groq.com/openai/v1/chat/completions';

export async function generateTanglishResponse(
  callerMessage,
  currentStatus,
  conversationHistory = []
) {
  try {
    console.log(`🤖 Generating Groq response for status: ${currentStatus}`);

    const systemPrompt = getReceptionistPrompt(currentStatus);

    // Build message history
    const messages = [
      {
        role: 'system',
        content: systemPrompt
      },
      ...conversationHistory,
      {
        role: 'user',
        content: callerMessage
      }
    ];

    const response = await axios.post(
      GROQ_API_URL,
      {
        model: process.env.GROQ_MODEL,
        messages: messages,
        temperature: 0.7,
        max_tokens: 150,
        top_p: 0.9
      },
      {
        headers: {
          Authorization: `Bearer ${process.env.GROQ_API_KEY}`,
          'Content-Type': 'application/json'
        }
      }
    );

    const generatedText = response.data.choices[0].message.content.trim();
    console.log(`✅ Groq response generated: ${generatedText.substring(0, 50)}...`);

    return {
      text: generatedText,
      status: currentStatus,
      model: process.env.GROQ_MODEL
    };
  } catch (error) {
    console.error('Error generating Groq response:', error.response?.data || error.message);
    throw new Error(`Failed to generate AI response: ${error.message}`);
  }
}
