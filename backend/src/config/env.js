export function validateEnvironment() {
  const required = [
    'PORT',
    'APP_SHARED_SECRET',
    'GROQ_API_KEY',
    'GROQ_MODEL',
    'META_ACCESS_TOKEN',
    'META_PHONE_NUMBER_ID',
    'META_VERIFY_TOKEN',
    'META_GRAPH_API_VERSION',
    'META_INITIAL_TEMPLATE',
    'META_TEMPLATE_LANGUAGE'
  ];

  const missing = required.filter(key => !process.env[key]);

  if (missing.length > 0) {
    throw new Error(
      `Missing required environment variables: ${missing.join(', ')}\n` +
      `See .env.example for required configuration.`
    );
  }

  // Validate format
  if (process.env.APP_SHARED_SECRET.length < 16) {
    throw new Error('APP_SHARED_SECRET must be at least 16 characters');
  }

  if (process.env.META_VERIFY_TOKEN.length < 16) {
    throw new Error('META_VERIFY_TOKEN must be at least 16 characters');
  }

  console.log('Environment config:');
  console.log(`  - PORT: ${process.env.PORT}`);
  console.log(`  - GROQ_MODEL: ${process.env.GROQ_MODEL}`);
  console.log(`  - META_GRAPH_API_VERSION: ${process.env.META_GRAPH_API_VERSION}`);
  console.log(`  - META_TEMPLATE: ${process.env.META_INITIAL_TEMPLATE}`);
  console.log(`  - META_LANGUAGE: ${process.env.META_TEMPLATE_LANGUAGE}`);
}
