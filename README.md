# Imran AI Receptionist

A complete Android + Backend system that provides intelligent call screening and AI-powered WhatsApp responses for incoming calls from unknown callers.

**Current Status: Complete Single-Stretch Implementation**

## Overview

When an unknown person calls Imran:

1. Android app detects incoming call
2. Checks if caller is in device contacts
3. If unknown → reads current Imran status (Work/Sleep/Outing)
4. Sends caller number + status securely to backend
5. Backend sends WhatsApp message via Meta Cloud API using approved template
6. Caller replies on WhatsApp
7. Backend processes with Groq AI → generates Tanglish response
8. Response sent back through WhatsApp Business API
9. Full conversation history saved locally and on backend

## Key Features

- ✅ Android call screening (CallScreeningService)
- ✅ Status management (Work / Sleep / Outing)
- ✅ Meta WhatsApp Business Platform integration
- ✅ Groq AI-powered Tanglish responses
- ✅ Conversation history & persistence
- ✅ Render deployment ready
- ✅ GitHub Actions Android APK build
- ✅ Mobile-only development workflow
- ✅ No SMS - WhatsApp only
- ✅ Privacy-first architecture (no secrets in APK/GitHub)

## Project Structure

```
Imran-AI-Receptionist/
│
├── android-app/                       # Kotlin Android application
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/imran/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── status/
│   │   │   │   │   ├── StatusViewModel.kt
│   │   │   │   │   └── StatusRepository.kt
│   │   │   │   ├── call/
│   │   │   │   │   ├── CallScreeningService.kt
│   │   │   │   │   ├── CallProcessor.kt
│   │   │   │   │   └── PhoneNormalizer.kt
│   │   │   │   ├── contacts/
│   │   │   │   │   └── ContactChecker.kt
│   │   │   │   ├── network/
│   │   │   │   │   ├── ApiService.kt
│   │   │   │   │   ├── ApiClient.kt
│   │   │   │   │   └── SecureAuthInterceptor.kt
│   │   │   │   ├── data/
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   ├── EventDao.kt
│   │   │   │   │   └── Event.kt
│   │   │   │   └── util/
│   │   │   │       └── Debouncer.kt
│   │   │   ├── res/
│   │   │   │   ├── layout/
│   │   │   │   ├── drawable/
│   │   │   │   └── values/
│   │   │   └── AndroidManifest.xml
│   │   ├── build.gradle.kts
│   │   └── ...
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle.properties
│
├── backend/                           # Node.js Express backend
│   ├── src/
│   │   ├── server.js
│   │   ├── config/
│   │   │   └── env.js
│   │   ├── routes/
│   │   │   ├── index.js
│   │   │   ├── health.js
│   │   │   ├── callFollowup.js
│   │   │   └── webhook.js
│   │   ├── controllers/
│   │   │   ├── callController.js
│   │   │   └── webhookController.js
│   │   ├── services/
│   │   │   ├── groqService.js
│   │   │   ├── whatsappService.js
│   │   │   ├── conversationService.js
│   │   │   └── contactEventService.js
│   │   ├── middleware/
│   │   │   ├── auth.js
│   │   │   ├── validation.js
│   │   │   ├── errorHandler.js
│   │   │   └── rateLimit.js
│   │   ├── prompts/
│   │   │   └── receptionistPrompt.js
│   │   ├── db/
│   │   │   └── models.js
│   │   └── utils/
│   │       ├── logger.js
│   │       └── phoneNormalizer.js
│   ├── package.json
│   ├── .env.example
│   ├── render.yaml
│   └── ...
│
├── .github/
│   └── workflows/
│       └── android-build.yml          # GitHub Actions APK builder
│
├── docs/
│   ├── MOBILE_SETUP.md                # Mobile development guide
│   ├── GITHUB_SETUP.md                # GitHub private repo & workflow
│   ├── GROQ_SETUP.md                  # Groq API key setup
│   ├── META_WHATSAPP_SETUP.md         # Meta WhatsApp configuration
│   ├── RENDER_DEPLOYMENT.md           # Render backend deployment
│   ├── ANDROID_LIMITATIONS.md         # Android API limitations
│   ├── TESTING.md                     # Complete testing guide
│   └── TROUBLESHOOTING.md             # Common issues & fixes
│
├── .gitignore
├── LICENSE (MIT)
└── README.md
```

## Quick Start

### 1. Mobile Development Setup
See [docs/MOBILE_SETUP.md](./docs/MOBILE_SETUP.md)
- Clone repo on Android
- Edit code with Termux + mobile editor
- Push changes to GitHub
- GitHub Actions builds APK automatically

### 2. Backend Deployment
See [docs/RENDER_DEPLOYMENT.md](./docs/RENDER_DEPLOYMENT.md)
- Deploy to Render with one click
- Add environment variables
- Health check at `/health`

### 3. Meta WhatsApp Setup
See [docs/META_WHATSAPP_SETUP.md](./docs/META_WHATSAPP_SETUP.md)
- Create WhatsApp Business Account
- Set up approved template
- Configure webhook
- Add environment variables

### 4. Groq AI Setup
See [docs/GROQ_SETUP.md](./docs/GROQ_SETUP.md)
- Get API key from Groq
- Add to Render environment
- System prompt configures dynamically

## Technology Stack

**Android:**
- Kotlin + MVVM
- Android CallScreeningService API (Android 10+)
- Room (local persistence)
- Retrofit (HTTP client)
- Coroutines (async)

**Backend:**
- Node.js 18+ + Express.js
- Groq API (LLM inference)
- Meta WhatsApp Cloud API
- PostgreSQL (optional, can use JSON storage)
- JWT/HMAC authentication

**Infrastructure:**
- Render (backend hosting)
- GitHub Actions (APK builds)
- GitHub Private Repository

## Important Implementation Notes

### ⚠️ Android Call Screening Reality

- Works on Android 10+ with `ROLE_CALL_SCREENING` permission
- Requires user to explicitly set app as default call screener
- Some devices/custom ROMs may restrict call interception
- See [docs/ANDROID_LIMITATIONS.md](./docs/ANDROID_LIMITATIONS.md)

### ⚠️ Meta WhatsApp Compliance

- Initial messages MUST use approved Meta template
- Dynamic AI replies only work within 24-hour conversation window
- After window expires, must use template again
- No unrestricted free-form proactive messages to arbitrary numbers
- Template must be approved before production use

### ⚠️ No SMS Used

- All external messaging uses WhatsApp Business API only
- No SmsManager or traditional SMS implementation
- No unofficial WhatsApp automation

## Documentation

- [Mobile Setup & Development](./docs/MOBILE_SETUP.md)
- [GitHub Private Repository Setup](./docs/GITHUB_SETUP.md)
- [Render Deployment Guide](./docs/RENDER_DEPLOYMENT.md)
- [Meta WhatsApp Configuration](./docs/META_WHATSAPP_SETUP.md)
- [Groq API Setup](./docs/GROQ_SETUP.md)
- [Android Limitations & Reality](./docs/ANDROID_LIMITATIONS.md)
- [Complete Testing Guide](./docs/TESTING.md)
- [Troubleshooting Common Issues](./docs/TROUBLESHOOTING.md)

## Environment Variables

### Backend (Render)

```
PORT=3000
APP_SHARED_SECRET=<long_random_value>

GROQ_API_KEY=<your_groq_api_key>
GROQ_MODEL=mixtral-8x7b-32768

META_ACCESS_TOKEN=<whatsapp_business_access_token>
META_PHONE_NUMBER_ID=<your_whatsapp_phone_number_id>
META_VERIFY_TOKEN=<another_long_random_value>
META_GRAPH_API_VERSION=v18.0

META_INITIAL_TEMPLATE=imran_call_followup
META_TEMPLATE_LANGUAGE=en_US
```

### Android (Local SharedPreferences)

- Current status (Work/Sleep/Outing)
- Backend URL (from BuildConfig)
- Auth token (derived from APP_SHARED_SECRET)

## Security

- ✅ No API keys in Android APK
- ✅ No secrets in GitHub repository
- ✅ Secrets only in Render environment
- ✅ Request validation on all endpoints
- ✅ Meta webhook signature verification
- ✅ HTTPS-only backend (Render)
- ✅ Phone number normalization
- ✅ Idempotency protection for duplicate events
- ✅ Rate limiting on webhook endpoint
- ✅ Sanitized logging (no secrets in logs)

## License

MIT License - See LICENSE file

## Author

Imran (@i-imran09)

---

**Built for mobile-first development. No PC or Android Studio required for normal workflow.**
