# Groq API Setup Guide

This document explains how to set up and configure the Groq API for the Imran AI Receptionist.

## What is Groq?

Groq is an AI inference engine that provides fast, cost-effective access to large language models like Mixtral, Llama 2, and Gemma.

For this project, Groq generates natural, conversational responses in Tanglish (Tamil-English mix) based on the caller's messages.

## Step 1: Create a Groq Account

1. Visit https://console.groq.com
2. Click **Sign Up**
3. Enter your email and password
4. Verify your email
5. Complete account setup

## Step 2: Create API Key

1. Log in to https://console.groq.com
2. Click **API Keys** in the left sidebar
3. Click **Create API Key**
4. Copy the key (starts with `gsk_`)
5. **Save this key securely**

**Example Key Format**: `gsk_dG9vYXJlYXdpY2gxNjkwMzUzNDAwMA==`

⚠️ **Important**: Never share this key. Keep it in `.env` only, not in code.

## Step 3: Add Key to Backend Configuration

1. Open `backend/.env`
2. Find the line: `GROQ_API_KEY=your_groq_api_key_here`
3. Replace with your actual key:
   ```
   GROQ_API_KEY=gsk_dG9vYXJlYXdpY2gxNjkwMzUzNDAwMA==
   ```
4. Save the file
5. Never commit this file to GitHub

## Step 4: Choose a Model

Groq supports several models. For this project, we recommend:

### Recommended: Mixtral 8x7B (Default)

**Model ID**: `mixtral-8x7b-32768`

**Characteristics**:
- Fast inference (~100ms)
- Good language understanding
- Handles Tanglish well
- 32K token context window
- Free tier available

**Set in `.env`**:
```
GROQ_MODEL=mixtral-8x7b-32768
```

### Alternative: Llama 2 70B

**Model ID**: `llama2-70b-4096`

**Characteristics**:
- Very capable
- Good for complex conversations
- Slightly slower than Mixtral
- 4K token context

**Set in `.env`**:
```
GROQ_MODEL=llama2-70b-4096
```

### Alternative: Gemma 7B

**Model ID**: `gemma-7b-it`

**Characteristics**:
- Fast and lightweight
- Good for simple responses
- Lower latency
- 8K token context

**Set in `.env`**:
```
GROQ_MODEL=gemma-7b-it
```

## Step 5: Check Available Models

To see all available models:

1. Go to https://console.groq.com/docs/models
2. List of all current models with capabilities
3. Check token context limits
4. Verify model is in free tier (if applicable)

## Step 6: Test Your Setup

### Test via Groq Console

1. Go to https://console.groq.com/playground
2. Select model: `mixtral-8x7b-32768`
3. Enter a test message:
   ```
   You are an AI receptionist speaking Tamil-English Tanglish.
   User: "I'm calling about a project quote"
   Respond naturally in Tanglish.
   ```
4. Click **Send**
5. Verify you get a response

### Test via Backend

Once backend is deployed:

1. Send test request:
   ```bash
   curl -X POST http://localhost:3000/api/test-groq \
     -H "Content-Type: application/json" \
     -d '{
       "message": "I am calling about a project"
     }'
   ```

2. You should get an AI response

## API Rate Limits

Groq Free Tier:

- **Requests per minute**: 30
- **Tokens per minute**: 6,000
- **Concurrent requests**: 1

For production deployment, check https://console.groq.com/account/usage for actual limits.

**Optimization Tips**:
- Limit message history to recent messages only
- Use shorter context when possible
- Batch similar requests

## Groq Service Implementation

The backend includes `groqService.js` which handles all Groq interactions:

```javascript
const groqService = require('./services/groqService');

// Generate AI response
const response = await groqService.generateResponse(
  systemPrompt,      // Behavioral instructions for status
  userMessage,       // Caller's message
  conversationHistory // Previous messages in conversation
);
```

**Parameters**:
- `systemPrompt`: Defines AI behavior based on current status (Work/Sleep/Outing)
- `userMessage`: The incoming caller message
- `conversationHistory`: Previous messages to maintain context

**Returns**: Natural language AI response

## System Prompt

The system prompt is dynamically generated based on status:

### Work Status

```
You are Imran's personal AI receptionist speaking Tamil-English Tanglish.
Imran is currently working.
Collect the caller's name, reason, urgency, and callback information.
```

### Sleep Status

```
You are Imran's personal AI receptionist speaking Tamil-English Tanglish.
Imran is currently resting/sleeping.
Politely collect information about the call and explain Imran will respond later.
```

### Outing Status

```
You are Imran's personal AI receptionist speaking Tamil-English Tanglish.
Imran is currently away/outside.
Collect the caller's information and explain Imran will respond when available.
```

## Tanglish Support

Tanglish is Tamil-English code-mixing. Groq handles this well with these models:

**Good Tanglish Examples**:
- "Enna reason-ku call pannenga?"
- "Naan Imran-oda AI assistant"
- "Ippo work-la irukkaru"

The system prompt guides the AI to respond naturally in this style.

## Troubleshooting

### Error: Invalid API Key

**Solution**:
1. Verify key format starts with `gsk_`
2. Check for extra spaces in `.env`
3. Generate new key from console if needed
4. Restart backend after updating key

### Error: Rate Limit Exceeded

**Cause**: Too many requests in short time

**Solution**:
1. Reduce request frequency
2. Implement request queuing
3. Upgrade to paid tier for higher limits
4. Cache common responses

### Error: Model Not Found

**Cause**: Model name is incorrect or deprecated

**Solution**:
1. Check current models at https://console.groq.com/docs/models
2. Update `GROQ_MODEL` in `.env`
3. Restart backend

### Slow Response Time

**Cause**: Model choice or network latency

**Solution**:
1. Try Gemma 7B for faster responses
2. Reduce context window (fewer historical messages)
3. Simplify system prompt
4. Check network latency

## Performance Optimization

### Limit Conversation History

```javascript
// Only keep last 5 messages for context
const recentMessages = conversationHistory.slice(-5);
```

### Cache Common Responses

```javascript
const responseCache = new Map();
if (responseCache.has(messageHash)) {
  return responseCache.get(messageHash);
}
```

### Set Appropriate Tokens

```javascript
// Don't generate excessively long responses
const response = await generateResponse(
  systemPrompt,
  userMessage,
  history,
  {
    max_tokens: 256  // Limit output length
  }
);
```

## Cost Estimation

Groq Free Tier: **$0** (generous limits)

If you exceed free tier:
- **Input tokens**: ~$0.00005 per 1K tokens
- **Output tokens**: ~$0.00015 per 1K tokens

**Example**: 100 conversations with 500 tokens input, 100 tokens output each:
- Input: 100 × 500 = 50,000 tokens = $0.0025
- Output: 100 × 100 = 10,000 tokens = $0.0015
- **Total**: ~$0.004 (less than half a cent)

## Production Considerations

1. **Monitoring**: Track API usage and costs
2. **Fallback**: Implement fallback response if Groq is unavailable
3. **Caching**: Cache responses for repeated queries
4. **Rate Limiting**: Respect Groq rate limits
5. **Error Handling**: Graceful degradation if API fails

## See Also

- [API_KEYS.md](API_KEYS.md) - API key configuration
- [DEPLOYMENT.md](DEPLOYMENT.md) - Backend deployment
- [TESTING.md](TESTING.md) - Testing procedures
