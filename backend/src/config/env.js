export function validateEnvironment() {
  const required = [
    "APP_CLIENT_TOKEN",
    "GROQ_API_KEY",
    "META_ACCESS_TOKEN",
    "META_PHONE_NUMBER_ID",
    "META_VERIFY_TOKEN",
    "META_APP_SECRET",
    "META_GRAPH_API_VERSION",
    "META_INITIAL_TEMPLATE"
  ];
  const missing = required.filter(k => !process.env[k]);
  if (missing.length) throw new Error(`Missing environment variables: ${missing.join(", ")}`);
}

export const getConfig = () => ({
  port: Number(process.env.PORT || 3000),
  groq: {
    apiKey: process.env.GROQ_API_KEY,
    model: process.env.GROQ_MODEL || "openai/gpt-oss-20b"
  },
  meta: {
    accessToken: process.env.META_ACCESS_TOKEN,
    phoneNumberId: process.env.META_PHONE_NUMBER_ID,
    verifyToken: process.env.META_VERIFY_TOKEN,
    appSecret: process.env.META_APP_SECRET,
    graphApiVersion: process.env.META_GRAPH_API_VERSION,
    initialTemplate: process.env.META_INITIAL_TEMPLATE,
    templateLanguage: process.env.META_TEMPLATE_LANGUAGE || "en_US"
  },
  app: { clientToken: process.env.APP_CLIENT_TOKEN }
});
