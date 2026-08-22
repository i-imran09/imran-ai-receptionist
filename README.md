# Imran AI Receptionist

A personal AI receptionist Android application that screens unknown calls and manages caller interactions via WhatsApp Business Platform using Groq AI.

## Features

- **Status Management**: Set your availability (Work, Sleep, Outing) from the Android app
- **Call Screening**: Identify and process unknown callers using Android call-screening APIs
- **Contact Detection**: Automatically ignore calls from saved contacts
- **WhatsApp Integration**: Send templated messages to unknown callers via Meta WhatsApp Business Platform
- **AI Responses**: Generate natural Tanglish responses using Groq API
- **Conversation History**: Track all interactions with caller timestamps and repeated calls
- **Mobile-First Development**: Fully deployable from Android using GitHub Actions cloud builds

## Project Structure

```
Imran-AI-Receptionist/
├── android-app/                  # Android Kotlin application
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/.../
│   │   │   │   ├── MainActivity
│   │   │   │   ├── status/
│   │   │   │   ├── call/
│   │   │   │   ├── contacts/
│   │   │   │   ├── network/
│   │   │   │   ├── data/
│   │   │   │   └── history/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── build.gradle.kts
│   │   └── ...
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── ...
├── backend/                      # Node.js Express backend
│   ├── src/
│   │   ├── server.js
│   │   ├── routes/
│   │   │   ├── callRoutes.js
│   │   │   └── webhookRoutes.js
│   │   ├── services/
│   │   │   ├── groqService.js
│   │   │   ├── whatsappService.js
│   │   │   ├── promptService.js
│   │   │   └── conversationService.js
│   │   ├── middleware/
│   │   │   ├── auth.js
│   │   │   ├── validation.js
│   │   │   └── errorHandler.js
│   │   ├── storage/
│   │   │   ├── storage.js
│   │   │   ├── jsonStorage.js
│   │   │   └── databaseAdapter.js
│   │   └── utils/
│   ├── package.json
│   └── .env.example
├── .github/
│   └── workflows/
│       └── android.yml           # GitHub Actions APK builder
├── docs/
│   ├── MOBILE_ONLY_SETUP.md      # Complete setup guide
│   ├── API_KEYS.md               # API key configuration
│   ├── META_WHATSAPP_SETUP.md    # Meta platform setup
│   ├── GROQ_SETUP.md             # Groq API setup
│   ├── TESTING.md                # Testing procedures
���   ├── ANDROID_LIMITATIONS.md    # Platform limitations
│   ├── DEPLOYMENT.md             # Backend deployment
│   └── SECURITY.md               # Security guidelines
├── .gitignore
├── LICENSE
└── README.md
```

## Quick Start

See [docs/MOBILE_ONLY_SETUP.md](docs/MOBILE_ONLY_SETUP.md) for complete mobile-only setup instructions.

## Requirements

- Android 8.0+ (API 26+)
- Groq API key
- Meta WhatsApp Business Platform account
- Backend deployment host (Heroku, Railway, etc.)

## Documentation

- [Mobile Setup Guide](docs/MOBILE_ONLY_SETUP.md)
- [API Key Configuration](docs/API_KEYS.md)
- [Meta WhatsApp Setup](docs/META_WHATSAPP_SETUP.md)
- [Groq API Setup](docs/GROQ_SETUP.md)
- [Testing Guide](docs/TESTING.md)
- [Android Limitations](docs/ANDROID_LIMITATIONS.md)
- [Deployment Guide](docs/DEPLOYMENT.md)
- [Security Guidelines](docs/SECURITY.md)

## Workflow

1. Unknown call arrives
2. Android app checks if caller is in contacts
3. If unknown, retrieves current status (Work/Sleep/Outing)
4. Sends status + caller number to backend
5. Backend sends Meta-approved WhatsApp template to caller
6. Caller replies on WhatsApp
7. Backend receives webhook, processes with Groq AI
8. AI generates Tanglish response respecting current status
9. Response sent via WhatsApp
10. Conversation history stored and displayed in Android app

## Security

- All API keys stored in backend environment variables only
- No secrets in Android APK
- Request validation on all backend endpoints
- Meta webhook signature verification
- HTTPS-only backend deployment

## License

MIT License

## Author

Imran - Personal AI Receptionist Project
