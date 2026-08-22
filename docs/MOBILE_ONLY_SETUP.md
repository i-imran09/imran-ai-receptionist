# Mobile-Only Setup Guide: Imran AI Receptionist

This guide walks you through the complete setup of the Imran AI Receptionist from an Android phone using GitHub Actions for cloud builds.

## Prerequisites

- Android phone with Android 8.0+ (API 26+)
- GitHub account
- Groq API account
- Meta WhatsApp Business Platform account
- Backend hosting provider (Heroku, Railway, Render, etc.)

## Complete Setup Sequence

### Step 1: Download and Extract Project

1. Download `Imran-AI-Receptionist-Mobile.zip` to your Android phone
2. Use a file manager or archive app to extract the ZIP
3. Note the extraction location (usually `/sdcard/Download/Imran-AI-Receptionist/`)

### Step 2: Create GitHub Repository

1. Open GitHub on your phone browser: https://github.com
2. Log in to your account
3. Create a new repository:
   - Click **+** icon → **New repository**
   - Repository name: `imran-ai-receptionist`
   - Description: "Personal AI Receptionist with WhatsApp & Groq"
   - Visibility: **Public** (required for GitHub Actions on free plan)
   - Click **Create repository**

### Step 3: Upload Project to GitHub

**Using GitHub Web Interface:**

1. Open your new repository in browser
2. Click **Add file** → **Upload files**
3. Extract the ZIP contents and prepare to upload:
   - `backend/`
   - `android-app/`
   - `docs/`
   - `.github/workflows/`
   - `.gitignore`
   - `README.md`
   - `LICENSE`

4. Upload all files maintaining the folder structure
5. Add commit message: "Initial project setup"
6. Click **Commit changes**

**Alternative: Using Termux (Optional)**

```bash
pkg update && pkg upgrade -y
pkg install git nodejs -y
cd /path/to/extracted/project
git init
git add .
git commit -m "Initial project setup"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/imran-ai-receptionist.git
git push -u origin main
```

### Step 4: Create Groq API Key

1. Visit https://console.groq.com
2. Sign up or log in
3. Go to **API Keys** section
4. Create a new API key
5. Copy the key (keep it secure, do not share)
6. Note the model name (default: `mixtral-8x7b-32768`)

### Step 5: Setup Meta WhatsApp Business Platform

#### 5a: Create Meta Developer App

1. Go to https://developers.facebook.com
2. Click **My Apps** → **Create App**
3. Choose **Business** as the app type
4. Fill in app details:
   - App Name: "Imran AI Receptionist"
   - App Contact Email: Your email
   - App Purpose: Customer service/messaging
5. Click **Create App**

#### 5b: Add WhatsApp Product

1. In your app dashboard, click **Add Product**
2. Find **WhatsApp** and click **Set up**
3. Choose **Cloud API** (not On-Premises)
4. Accept terms and click **Continue**

#### 5c: Get Required IDs and Tokens

1. Go to **App Settings** → **Basic**:
   - Copy **App ID**
   - Copy **App Secret**

2. Go to **WhatsApp** → **Getting Started**:
   - Copy **Phone Number ID**
   - Note the **Business Account ID**

3. Go to **WhatsApp** → **API Setup**:
   - Under **Temporary access token**, click **Generate token**
   - Copy the token (valid for 24 hours)
   - For production, create a **System User** with permanent token:
     - Go to Settings → System users
     - Create new system user
     - Assign "Admin" role to WhatsApp product
     - Generate access token

#### 5d: Verify Your WhatsApp Phone Number

1. In WhatsApp settings, click **Add Phone Number**
2. Provide a valid phone number you own
3. Verify via SMS or call
4. Complete verification

### Step 6: Create WhatsApp Message Template

1. In WhatsApp settings, go to **Message Templates**
2. Click **Create New Template**
3. Template name: `call_followup`
4. Language: **English** (or your preference)
5. Category: **Marketing** or **Utility** (recommend **Utility** for call followups)
6. Template content:

```
Hi {{1}},

You recently tried to contact Imran. His current availability is {{1}}.

Please reply with the reason for your call, and his AI assistant will help collect the details.

Thank you!
```

7. Add example values (required for approval)
8. Click **Submit for Approval**

**Note:** Template approval typically takes 1-5 minutes but can take up to 24 hours. Meta will review for compliance.

### Step 7: Configure Backend Deployment

**Choose one option:**

#### Option A: Heroku (Recommended, Free Tier Discontinued)

Use alternative providers below.

#### Option B: Railway.app

1. Go to https://railway.app
2. Click **Start Project**
3. Click **Deploy from GitHub repo**
4. Connect GitHub and authorize
5. Select your `imran-ai-receptionist` repository
6. Railway will auto-detect Node.js
7. Click **Deploy**
8. Go to **Environment** tab, add variables:
   - `PORT`: `3000`
   - `GROQ_API_KEY`: Your Groq key
   - `GROQ_MODEL`: `mixtral-8x7b-32768`
   - `META_ACCESS_TOKEN`: Your Meta token
   - `META_PHONE_NUMBER_ID`: Your Phone Number ID
   - `META_VERIFY_TOKEN`: Create a random string (e.g., `random_token_123abc`)
   - `META_GRAPH_API_VERSION`: `v18.0`
   - `META_INITIAL_TEMPLATE`: `call_followup`
   - `META_TEMPLATE_LANGUAGE`: `en`
   - `ANDROID_SECRET_KEY`: Create a random secret (e.g., `dev_secret_123xyz`)

9. Click **Deploy** again
10. Copy the public URL (e.g., `https://your-project.up.railway.app`)

#### Option C: Render.com

1. Go to https://render.com
2. Sign up with GitHub
3. Click **New +** → **Web Service**
4. Connect GitHub repo
5. Select `imran-ai-receptionist`
6. Configuration:
   - Name: `imran-ai-receptionist`
   - Runtime: `Node`
   - Build command: `npm install`
   - Start command: `npm start`
   - Environment: **Free** or **Paid**

7. Add environment variables (same as Railway)
8. Click **Create Web Service**
9. Wait for deployment
10. Copy the public URL

### Step 8: Test Backend Health

1. Open browser on your phone
2. Visit: `https://YOUR_BACKEND_URL/health`
3. You should see:
   ```json
   {
     "status": "ok",
     "timestamp": "2024-XX-XXTXX:XX:XXZ",
     "uptime": X.XXX
   }
   ```
4. If successful, proceed to Step 9
5. If error, check:
   - Environment variables are set correctly
   - Backend deployment completed
   - No syntax errors in backend code

### Step 9: Configure Meta Webhook

1. Go to Meta app dashboard
2. Go to **WhatsApp** → **Configuration**
3. Under **Webhook URL**:
   - URL: `https://YOUR_BACKEND_URL/webhook`
   - Verify Token: Use the same `META_VERIFY_TOKEN` from Step 7
4. Click **Verify and Save**
5. Meta will verify the webhook (should return 200 with challenge)
6. If successful, you'll see **"Verified"**

### Step 10: Subscribe to Webhook Events

1. Still in **Configuration**
2. Under **Webhook Fields**:
   - Check **messages**
   - Check **message_status** (optional)
3. Click **Save**
4. Verify subscriptions are active

### Step 11: Push Code to GitHub

1. If you haven't already, push the backend environment variables to GitHub secrets:
   - Go to repository **Settings** → **Secrets and variables** → **Actions**
   - Create secrets for sensitive values (optional, for CI/CD)

2. Verify all files are committed:
   ```bash
   git status
   git add .
   git commit -m "Add environment configuration"
   git push origin main
   ```

### Step 12: Build Android APK using GitHub Actions

1. Open your repository on GitHub (phone browser)
2. Click **Actions** tab
3. Click **Build Android APK** workflow on the left
4. Click **Run workflow** → **Run workflow** button
5. Wait for build to complete (usually 3-5 minutes)
6. When build finishes (green checkmark):
   - Click the workflow run
   - Scroll down to **Artifacts**
   - Download `app-debug.apk`

### Step 13: Install APK on Android Phone

1. Download the APK to your phone's Downloads folder
2. Open Files/File Manager app
3. Navigate to **Downloads** folder
4. Tap the APK file
5. Android will prompt for installation:
   - Allow unknown app sources if prompted
   - Click **Install**
6. Once installed, click **Open** or find "Imran AI Receptionist" in app drawer

### Step 14: Grant App Permissions

When you first open the app, Android will request:

- **Read Phone State**: Allow (needed to detect incoming calls)
- **Read Contacts**: Allow (needed to check if caller is saved)
- **Internet**: Allow (needed for WhatsApp backend communication)

Grant all requested permissions.

### Step 15: Enable Call Screening (Android 10+)

**This step is optional but recommended:**

1. Open **Settings** → **Apps** → **Default apps**
2. Look for **Call screening app** or **Caller ID and spam protection**
3. Select **Imran AI Receptionist**
4. Or go to **Settings** → **Calling accounts** → **Call screening**
5. Note: Some devices may require setting the app as the default dialer first

**If your device doesn't support CallScreeningService:**

- The app will fall back to `PhoneCallReceiver`
- Call detection may not be reliable on all devices
- See [ANDROID_LIMITATIONS.md](docs/ANDROID_LIMITATIONS.md)

### Step 16: Set Your Availability Status

1. Open the Imran AI Receptionist app
2. Tap one of the status buttons:
   - **Set Work**: Status = Work
   - **Set Sleep**: Status = Sleep
   - **Set Outing**: Status = Outing
3. The status will persist even after closing the app
4. Verify in the "Current Status" display

### Step 17: Test with Saved Contact

1. Call the app from a phone number already in your contacts
2. The app should ignore this call (no WhatsApp message sent)
3. Verify in the call history

### Step 18: Test with Unknown Number

1. Call the app from an unknown phone number (ask a friend to call from their phone)
2. Wait for the call to ring
3. Check WhatsApp for incoming message from Meta:
   - Should contain the template with your current status
   - Message arrives within seconds
4. Verify in app call history that the call was recorded

### Step 19: Reply from WhatsApp

1. Open the WhatsApp message from Meta
2. Reply with reason for your call:
   - Example: "I'm calling about your project quote"
3. Send the message
4. Wait 5-10 seconds

### Step 20: Verify AI Response

1. Check WhatsApp for AI response from Imran's assistant
2. Response should be in natural Tanglish (Tamil-English mix)
3. Example response:
   - "Naan Imran-oda AI assistant. Imran ippo work-la irukkaru. Enna reason-ku call pannenga na sollingeeh, note pannikiren." (if status was Work)
4. The response shows Groq AI is working correctly

### Step 21: Verify Conversation History

1. Open the Imran AI Receptionist app
2. Scroll down to "Call History"
3. You should see:
   - The unknown caller's phone number
   - The timestamp of the call
   - The current status (Work/Sleep/Outing)
   - Previous message count

### Step 22: Test Repeated Caller

1. Call from the same unknown number again (within a few hours)
2. App should detect it's the same caller
3. History should show:
   - Total calls: 2 (or more)
   - All previous call timestamps
   - Combined conversation history
4. No duplicate WhatsApp template sent (only once per conversation window)

### Step 23: Verify Complete Workflow

Your complete workflow is now:

```
Unknown Call → Android detects → Checks contacts → Sends to backend →
Backend generates WhatsApp template → Caller receives on WhatsApp →
Caller replies → Backend receives webhook → Groq AI generates response →
Response sent via WhatsApp → History stored in Android app
```

## Troubleshooting

### APK Build Failed

1. Check GitHub Actions workflow logs:
   - Go to repository **Actions** tab
   - Click the failed workflow
   - Click the job for details
   - Look for error messages
2. Common causes:
   - Gradle cache issues: Wait 10 minutes and retry
   - Java version mismatch: Workflow uses Java 11
   - File permissions: Ensure all files pushed correctly
3. Solution: Try workflow again or check [TESTING.md](docs/TESTING.md)

### Backend Health Check Fails

1. Verify deployment is active:
   - Go to Railway/Render dashboard
   - Check deployment status (should be "Running")
2. Verify environment variables:
   - All required variables set
   - No typos or missing values
3. Check backend logs:
   - In Railway/Render, click **Logs** tab
   - Look for startup errors
4. Restart deployment if needed

### No WhatsApp Messages Received

1. Verify Meta credentials:
   - Token is current (Railway tokens expire after some time)
   - Phone Number ID is correct
   - Phone number is verified
2. Verify template:
   - Template is approved (status should be "APPROVED" in Meta dashboard)
   - Template name matches `META_INITIAL_TEMPLATE` env var
   - Language matches `META_TEMPLATE_LANGUAGE`
3. Check backend logs for errors
4. Test manually: See [TESTING.md](docs/TESTING.md)

### Call History Empty

1. Verify permissions:
   - Go to **Settings** → **Apps** → **Imran AI Receptionist**
   - Check **Permissions** tab
   - Ensure "Read Phone State" and "Read Contacts" are granted
2. Check if calls are actually being registered:
   - Check call log in default phone app
3. Check Android version:
   - Some devices may have OS-level restrictions
   - See [ANDROID_LIMITATIONS.md](docs/ANDROID_LIMITATIONS.md)

### App Crashes on Startup

1. Check logcat:
   ```bash
   adb logcat | grep Imran
   ```
2. Common causes:
   - Missing permissions
   - Storage access denied
   - Backend URL incorrect
3. Reinstall app:
   - Uninstall from Settings → Apps
   - Clear app data if prompted
   - Rebuild and install from GitHub Actions

## Next Steps

1. **Production Deployment**: Replace dev secrets with production keys
2. **Database**: Integrate with permanent database (PostgreSQL, MongoDB)
3. **Advanced Filtering**: Add spam detection, custom routing
4. **Additional Languages**: Support more Tamil-English variants
5. **Integrations**: Connect to CRM, ticket systems, etc.

For detailed information on each service, see:
- [API_KEYS.md](docs/API_KEYS.md) - API key configuration
- [META_WHATSAPP_SETUP.md](docs/META_WHATSAPP_SETUP.md) - WhatsApp platform details
- [GROQ_SETUP.md](docs/GROQ_SETUP.md) - Groq API setup
- [TESTING.md](docs/TESTING.md) - Detailed testing procedures
- [ANDROID_LIMITATIONS.md](docs/ANDROID_LIMITATIONS.md) - Platform constraints
- [DEPLOYMENT.md](docs/DEPLOYMENT.md) - Backend deployment options
- [SECURITY.md](docs/SECURITY.md) - Security best practices

## Support

For issues, questions, or contributions, open an issue on GitHub:
https://github.com/i-imran09/imran-ai-receptionist/issues
