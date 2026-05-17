# Simple Journaling App — Setup

## One-time Google Cloud Console setup

1. Go to https://console.cloud.google.com/ and create a new project (e.g. "Simple Journaling App").
2. Under **APIs & Services → Library**, enable both:
   - Google Docs API
   - Google Drive API
3. Under **APIs & Services → OAuth consent screen**:
   - User type: **External**
   - Fill required fields (app name, support email)
   - **Test users**: add your own Gmail address
   - Scopes (added automatically when you sign in, but you can pre-declare them):
     - `https://www.googleapis.com/auth/documents`
     - `https://www.googleapis.com/auth/drive.file`
   - Leave the app in **Testing** mode; no review needed for personal use.
4. Under **APIs & Services → Credentials**:
   - Click **Create credentials → OAuth client ID**
   - Application type: **Android**
   - Package name: `com.ziiqiio.simplejournalingapp`
   - SHA-1 certificate fingerprint: get it with
     ```powershell
     ./gradlew signingReport
     ```
     and paste the `SHA1` from the `debug` variant. (For a release build, register your release keystore's SHA-1 too.)
5. No client secret is needed — the SHA-1 + package name pair is what authenticates the app.

## First-run flow

1. Build & install the app on a device or emulator running Android 13+.
2. Tap the gear icon (Settings).
3. Tap **Sign in with Google**, pick your account, grant Docs + Drive scopes.
4. Tap back. Write your first entry and tap **Save Entry**.
5. The first sync will:
   - Create a folder named **Journal** in your Drive (if one isn't already there).
   - Create a Doc named `Journal — May 2026` inside it.
   - Append your entry.
6. The pending entry's status flips to **Synced**; tap **Added to Google Doc — Dismiss** to clear it from the local list.

## Notes

- **Time zone:** entries use the phone's current date and time settings.
- **Override Doc ID:** if you want everything to go into one specific existing Doc instead of monthly Docs, paste its ID in Settings (the ID is the long string in the Doc's URL between `/d/` and `/edit`).
- **Offline:** entries save locally immediately; WorkManager retries with exponential backoff until the network comes back.
- **Prompts:** the last 20 prompts are excluded from random selection.

## Troubleshooting

- **403 `SERVICE_DISABLED` / `accessNotConfigured`:** the Google Cloud project attached to your Android OAuth client does not have the required API enabled. Open the activation URL from the log, or go to **APIs & Services -> Library** in that project and enable **Google Drive API** and **Google Docs API**. Wait a few minutes for the change to propagate, then tap **Retry Sync** in the app.
