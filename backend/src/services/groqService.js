import axios from 'axios';
import { getConfig } from '../config/env.js';

const config = getConfig();

export const generateAIResponse = async (systemPrompt, userMessage, messageHistory = []) => {
  try {
    const response = await axios.post(
      'https://api.groq.com/openai/v1/chat/completions',
      {
        model: config.groq.model,
        messages: [
          { role: 'system', content: systemPrompt },
          ...messageHistory,
          { role: 'user', content: userMessage }
        ],
        temperature: config.groq.temperature,
        max_tokens: config.groq.maxTokens
      },
      {
        headers: {
          'Authorization': `Bearer ${config.groq.apiKey}`,
          'Content-Type': 'application/json'
        },
        timeout: 30000
      }
    );

    if (response.data?.choices?.[0]?.message?.content) {
      return response.data.choices[0].message.content.trim();
    }
    throw new Error('Unexpected Groq response format');
  } catch (error) {
    console.error('[Groq Error]', error.response?.data || error.message);
    return 'Sorry, I am unable to process your request at the moment. Please try again later.';
  }
};
