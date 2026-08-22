const axios = require('axios');

const GROQ_API_URL = 'https://api.groq.com/openai/v1/chat/completions';
const GROQ_API_KEY = process.env.GROQ_API_KEY;
const GROQ_MODEL = process.env.GROQ_MODEL || 'mixtral-8x7b-32768';

async function generateResponse(systemPrompt, userMessage, conversationHistory = []) {
  if (!GROQ_API_KEY) {
    throw new Error('GROQ_API_KEY not configured');
  }

  try {
    // Build message history
    const messages = [
      { role: 'system', content: systemPrompt },
      ...conversationHistory.map(msg => ({
        role: msg.type === 'outgoing' ? 'assistant' : 'user',
        content: msg.text
      })),
      { role: 'user', content: userMessage }
    ];

    const response = await axios.post(
      GROQ_API_URL,
      {
        model: GROQ_MODEL,
        messages: messages,
        temperature: 0.7,
        max_tokens: 256,
        top_p: 0.9
      },
      {
        headers: {
          'Authorization': `Bearer ${GROQ_API_KEY}`,
          'Content-Type': 'application/json'
        },
        timeout: 30000
      }
    );

    if (response.data && response.data.choices && response.data.choices[0]) {
      const aiResponse = response.data.choices[0].message.content.trim();
      console.log(`[${new Date().toISOString()}] Groq response generated:`, {
        model: GROQ_MODEL,
        inputTokens: response.data.usage?.prompt_tokens,
        outputTokens: response.data.usage?.completion_tokens
      });
      return aiResponse;
    } else {
      throw new Error('Unexpected Groq API response format');
    }
  } catch (error) {
    if (error.response) {
      console.error(`[${new Date().toISOString()}] Groq API error:`, {
        status: error.response.status,
        message: error.response.data?.error?.message || 'Unknown error'
      });
      throw new Error(`Groq API error: ${error.response.data?.error?.message || 'Unknown'}`);
    } else if (error.code === 'ECONNABORTED') {
      throw new Error('Groq API request timeout');
    } else {
      throw error;
    }
  }
}

module.exports = {
  generateResponse
};
