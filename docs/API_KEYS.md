# API Keys and Environment Variables Configuration

This document explains exactly where each API key and environment variable goes.

## Backend Configuration (.env file)

Create a `.env` file in the `backend/` directory with these variables:

```
# Server Configuration
PORT=3000
NODE_ENV=production

# Groq API Configuration
GROQ_API_KEY=gsk_xxxxxxxxxxxxxxxxxxxxxxxxxxxxx
GROQ_MODEL=mixtral-8x7b-32768

# Meta WhatsApp Business Platform
META_ACCESS_TOKEN=EAAxxxxxxxxxxxxxxxxxxxxxxxxxx
META_PHONE_NUMBER_ID=1234567890123456
META_APP_SECRET=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
META_VERIFY_TOKEN=your_random_verify_token_here
META_GRAPH_API_VERSION=v18.0
META_INITIAL_TEMPLATE=call_followup
META_TEMPLATE_LANGUAGE=en

# Android Backend Authentication (Change in Production!)
ANDROID_SECRET_KEY=dev_secret_change_in_production

# Storage Configuration
STORAGE_TYPE=json
STORAGE_PATH=./storage

# Logging
LOG_LEVEL=info
```

## Where to Find Each Value

### GROQ_API_KEY

**Location**: Groq Console

1. Go to https://console.groq.com
2. Click **API Keys** in left sidebar
3. Click **Create API Key**
4. Copy the key starting with `gsk_`
5. Example: `gsk_dG9vYXJlYXdpY2gxNjkwMzUzNDAwMA==`

**Usage in Backend**:
```javascript
const GROQ_API_KEY = process.env.GROQ_API_KEY; // "gsk_..."
```

### GROQ_MODEL

**Options**:
- `mixtral-8x7b-32768` (recommended, free tier)
- `llama2-70b-4096`
- `gemma-7b-it`

**Get current list**:
1. Go to https://console.groq.com/docs/speech-text
2. Check "Models" section for available options

### META_ACCESS_TOKEN

**Temporary Token** (24 hours):
1. Go to https://developers.facebook.com/apps
2. Select your app
3. Go to **WhatsApp** → **API Setup**
4. Click **Generate Token** under "Temporary access token"
5. Copy the token starting with `EAXY...`

**Permanent Token** (Recommended for Production):
1. Go to **Settings** → **System Users**
2. Create new system user or use existing
3. Assign admin role to WhatsApp product
4. Click **Generate Token**
5. Copy the token

**Example**: `EAAxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

### META_PHONE_NUMBER_ID

**Location**: Meta WhatsApp Setup

1. Go to https://developers.facebook.com/apps
2. Select your app
3. Go to **WhatsApp** → **Getting Started**
4. Under "Phone Number Management", find your phone number
5. Copy the **Phone Number ID** (numeric, like `1234567890123456`)

**Usage**: This is the WhatsApp Business phone number you're using

### META_APP_SECRET

**Location**: App Settings

1. Go to https://developers.facebook.com/apps
2. Select your app
3. Go to **Settings** → **Basic**
4. Under **App Credentials**, find **App Secret**
5. Click the eye icon to reveal it
6. Copy the value

**Used for**: Webhook signature verification

### META_VERIFY_TOKEN

**Create Your Own Random String**:

This is NOT provided by Meta. You create it:

```bash
# Generate a random token
openssl rand -hex 32
# or just use a random string like:
verify_token_abc123xyz789
```

**Where to Use**:
1. Save in your `.env` as `META_VERIFY_TOKEN=your_token`
2. Go to Meta app dashboard
3. Go to **WhatsApp** → **Configuration**
4. Under **Webhook URL** section, paste the same token in **Verify Token** field
5. Click **Verify and Save**

Meta will verify that your backend returns the correct challenge with this token.

### META_GRAPH_API_VERSION

**Check Latest Version**:
1. Go to https://developers.facebook.com/docs/graph-api/overview
2. Find current stable version
3. Example: `v18.0`, `v17.0`

**Typical Value**: `v18.0` (latest stable)

### META_INITIAL_TEMPLATE

**Value**: The exact name of your approved WhatsApp template

**Steps to Create**:
1. Go to Meta app dashboard
2. **WhatsApp** → **Message Templates**
3. Click **Create Template**
4. Template Name: `call_followup` (or your chosen name)
5. Category: **Utility** or **Marketing**
6. Content: Your template text with placeholders
7. Submit for approval

**In .env**: Use the exact template name you created
```
META_INITIAL_TEMPLATE=call_followup
```

**Important**: Template must be **APPROVED** before use
- Check approval status in Message Templates section
- Status should show "APPROVED"
- Approval usually takes 5 minutes to 24 hours

### META_TEMPLATE_LANGUAGE

**Common Values**:
- `en` - English
- `es` - Spanish
- `ta` - Tamil
- `hi` - Hindi

**Get Language Code**:
1. Go to https://developers.facebook.com/docs/whatsapp/api/messages/message-templates
2. Check "Supported Languages" section

**Must Match Template**: The language in `.env` must match the language you created the template in

### ANDROID_SECRET_KEY

**Development**: Use the provided default
```
ANDROID_SECRET_KEY=dev_secret_change_in_production
```

**Production**: Create a strong random key
```bash
openssl rand -hex 32
# Result: abc123def456ghi789jkl012mno345pqr
```

**Then update in**:
1. Backend `.env`
2. Android app configuration (CallFollowupClient)
3. Both must match exactly

### STORAGE_TYPE

**Options**:
- `json` - File-based JSON storage (development/small scale)
- `database` - Database adapter (production ready, requires additional config)

**For Development**: Use `json`
**For Production**: Implement database adapter

### STORAGE_PATH

**Default**: `./storage` (relative to backend root)

**Directory Structure Created**:
```
backend/
├── storage/
│   └── conversations/
│       ├── 9198765432.json
│       ├── 9187654321.json
│       └── ...
```

### LOG_LEVEL

**Options**:
- `error` - Only errors
- `warn` - Warnings and errors
- `info` - Information, warnings, errors (recommended)
- `debug` - Verbose logging

## Android App Configuration

### Backend URL

In `CallFollowupClient.kt`:

```kotlin
class CallFollowupClient(
    private val context: Context,
    private val backendUrl: String = "http://192.168.1.100:3000",  // ← Change this
    private val secretKey: String = "dev_secret_change_in_production" // ← Match .env
)
```

**Update to your backend URL**:
- Development (Termux): `http://192.168.1.100:3000`
- Production (Railway): `https://your-project.up.railway.app`
- Production (Render): `https://your-service.onrender.com`

**Important**:
- Use `https://` for production (not `http://`)
- Must match backend deployment URL
- Use `http://` only for local testing

### Android Secret Key

Must match `ANDROID_SECRET_KEY` in backend `.env`

```kotlin
private val secretKey: String = "dev_secret_change_in_production" // Match .env
```

## Environment Variable Checklist

Before deploying, verify:

- [ ] `GROQ_API_KEY` - valid key from Groq console
- [ ] `GROQ_MODEL` - valid model name
- [ ] `META_ACCESS_TOKEN` - current token (not expired)
- [ ] `META_PHONE_NUMBER_ID` - correct numeric ID
- [ ] `META_APP_SECRET` - used for webhook verification
- [ ] `META_VERIFY_TOKEN` - random string you created
- [ ] `META_GRAPH_API_VERSION` - matches current Meta API version
- [ ] `META_INITIAL_TEMPLATE` - exact approved template name
- [ ] `META_TEMPLATE_LANGUAGE` - matches template language
- [ ] `ANDROID_SECRET_KEY` - matches value in Android app
- [ ] `STORAGE_TYPE` - set to `json` or `database`
- [ ] `STORAGE_PATH` - writable directory
- [ ] Backend URL in Android app matches deployment URL

## Security Notes

⚠️ **NEVER**:
- Commit `.env` file to GitHub
- Share API keys publicly
- Use same key for multiple services
- Use weak or guessable tokens

✅ **ALWAYS**:
- Use `.gitignore` to exclude `.env`
- Rotate tokens regularly in production
- Use strong random values for custom tokens
- Use HTTPS for production backends
- Set environment variables in deployment platform (not in code)

## Platform-Specific Setup

### Railway.app

Environment variables are set in the dashboard:
1. Click your project
2. Go to **Environment** tab
3. Add each variable as key=value
4. Changes apply immediately

### Render.com

1. Go to your Web Service
2. Click **Environment**
3. Add variables
4. Redeploy for changes to take effect

### Heroku (Legacy)

```bash
heroku config:set GROQ_API_KEY=gsk_xxx
heroku config:set META_ACCESS_TOKEN=EAA_xxx
# ... etc
```

## Troubleshooting

### Backend Returns 401 Unauthorized

**Cause**: Invalid or expired `META_ACCESS_TOKEN`

**Solution**:
1. Generate new token from Meta dashboard
2. Update `META_ACCESS_TOKEN` in platform environment
3. Redeploy or restart backend

### WhatsApp Template Not Found

**Cause**: Template name doesn't match or not approved

**Solution**:
1. Go to Meta **Message Templates**
2. Find your template
3. Copy exact name (case-sensitive)
4. Verify status is "APPROVED"
5. Update `META_INITIAL_TEMPLATE` in `.env`
6. Redeploy backend

### Webhook Verification Failed

**Cause**: `META_VERIFY_TOKEN` mismatch

**Solution**:
1. Generate new random token
2. Update `.env`: `META_VERIFY_TOKEN=new_token`
3. Redeploy backend
4. In Meta dashboard, enter the same token
5. Click "Verify and Save"

## See Also

- [META_WHATSAPP_SETUP.md](META_WHATSAPP_SETUP.md) - Detailed Meta setup
- [GROQ_SETUP.md](GROQ_SETUP.md) - Groq API documentation
- [DEPLOYMENT.md](DEPLOYMENT.md) - Backend deployment guide
