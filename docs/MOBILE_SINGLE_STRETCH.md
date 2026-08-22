# Mobile-only single-stretch setup (Tanglish)

1. Indha fixed ZIP-a extract panni repository root files-a GitHub repo-ku update pannu.
2. GitHub repo Settings -> Secrets and variables -> Actions -> New repository secret:
   - BACKEND_URL = Render URL with trailing `/`
   - APP_CLIENT_TOKEN = long random token; Render-la same value.
3. Render-la New -> Blueprint -> repo connect -> root `render.yaml` select.
4. Render secret environment values fill pannu:
   APP_CLIENT_TOKEN, GROQ_API_KEY, META_ACCESS_TOKEN, META_PHONE_NUMBER_ID,
   META_VERIFY_TOKEN, META_APP_SECRET, META_GRAPH_API_VERSION.
5. `META_GRAPH_API_VERSION` value-a current Meta Developer dashboard/docs-la supported version-aa set pannu.
6. Meta-la approved template `imran_call_followup` create pannu. Body-la exactly one variable `{{1}}` current status-ku use pannu.
7. Render deploy green aana `/health` open panni test pannu.
8. Meta webhook callback: `https://YOUR-SERVICE.onrender.com/webhook`.
   Verify token = Render `META_VERIFY_TOKEN`.
9. GitHub Actions -> Build Android APK -> Run workflow.
10. Green tick vandha artifact `Imran-AI-Receptionist-debug` download panni ZIP extract -> APK install.
11. App open -> Enable AI Receptionist -> Contacts permission + Call Screening role grant pannu.
12. Work/Sleep/Outing select panni unknown-number test call pannu.
13. Initial WhatsApp template caller-ku varanum. Caller reply pannina webhook -> Groq -> WhatsApp AI reply varanum.

Important:
- Normal SMS use panna maatadhu.
- Saved contacts ignore pannum.
- Android system multiple dialogs kaatta mudiyum; app one onboarding button-la sequence start pannum.
- APP_CLIENT_TOKEN APK-la extract panna mudiyum; idhu personal-app lightweight gate mattum. Provider secrets (Groq/Meta) APK-la illa.
- Backend JSON storage Render ephemeral filesystem-la permanent guarantee illa. Production history-ku managed Postgres later add pannradhu recommended.
