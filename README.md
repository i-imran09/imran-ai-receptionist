# Imran AI Receptionist — Fixed Mobile Build

Android 11-compatible personal receptionist foundation:
unknown caller -> CallScreeningService -> saved-contact check -> current status ->
Render backend -> approved WhatsApp template -> caller reply -> Meta webhook ->
Groq -> Tanglish response.

## What this fixed package repairs
- valid Kotlin Gradle DSL
- correct `android.telecom.CallScreeningService` manifest action
- one coherent call-processing path
- persistent Work/Sleep/Outing using DataStore
- Room call history + duplicate suppression
- correct `graph.facebook.com` WhatsApp Cloud API endpoint
- Meta webhook signature verification using raw request bytes
- current Groq production default `openai/gpt-oss-20b`
- root Render Blueprint with `rootDir: backend`
- GitHub Actions APK build workflow
- secrets excluded from source

See `docs/MOBILE_SINGLE_STRETCH.md`.

## External configuration still required
Meta credentials/template approval, current supported Meta Graph API version,
Groq key, Render environment variables, GitHub Actions secrets, Android permissions/role.

## Known limitation
Backend conversation JSON storage is not guaranteed persistent on Render's ephemeral
filesystem. Use a managed persistent database before relying on long-term history.
