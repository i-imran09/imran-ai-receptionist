export const validateEnvironment = () => {
  const requiredEnvVars = [
    'GROQ_API_KEY',
    'META_ACCESS_TOKEN',
    'META_PHONE_NUMBER_ID',
    'META_VERIFY_TOKEN'
  ];

  const missing = requiredEnvVars.filter(varName => !process.env[varName]);

  if (missing.length > 0) {
    throw new Error(`Missing required environment variables: ${missing.join(', ')}`);
  }

  // Validate format
  if (process.env.META_PHONE_NUMBER_ID && !/^\d+$/.test(process.env.META_PHONE_NUMBER_ID)) {
    throw new Error('META_PHONE_NUMBER_ID must be numeric');
  }
};

export const getConfig = () => ({
  port: process.env.PORT || 3000,
  nodeEnv: process.env.NODE_ENV || 'development',
  groq: {
    apiKey: process.env.GROQ_API_KEY,
    model: process.env.GROQ_MODEL || 'mixtral-8x7b-32768',
    maxTokens: 256,
    temperature: 0.7
  },
  meta: {
    accessToken: process.env.META_ACCESS_TOKEN,
    phoneNumberId: process.env.META_PHONE_NUMBER_ID,
    verifyToken: process.env.META_VERIFY_TOKEN,
    graphApiVersion: process.env.META_GRAPH_API_VERSION || 'v18.0',
    appSecret: process.env.META_APP_SECRET,
    initialTemplate: process.env.META_INITIAL_TEMPLATE || 'imran_call_followup',
    templateLanguage: process.env.META_TEMPLATE_LANGUAGE || 'en'
  },
  app: {
    sharedSecret: process.env.APP_SHARED_SECRET,
    maxRequestBodySize: '10mb'
  }
});
