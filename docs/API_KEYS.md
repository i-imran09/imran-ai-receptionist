# API / Secret Setup

Real secrets-a GitHub source-la commit panna koodathu.

## Render secrets
- APP_CLIENT_TOKEN
- GROQ_API_KEY
- META_ACCESS_TOKEN
- META_PHONE_NUMBER_ID
- META_VERIFY_TOKEN
- META_APP_SECRET
- META_GRAPH_API_VERSION

`META_GRAPH_API_VERSION`-ku old hardcoded value use panna koodathu. Setup time-la Meta official/current supported Graph API version-a check panni Render-la enter pannu.

## Non-secret defaults
- GROQ_MODEL=openai/gpt-oss-20b
- META_INITIAL_TEMPLATE=imran_call_followup
- META_TEMPLATE_LANGUAGE=en_US

## GitHub Actions secrets
- BACKEND_URL
- APP_CLIENT_TOKEN

`BACKEND_URL` Render HTTPS URL; trailing slash include pannu.
