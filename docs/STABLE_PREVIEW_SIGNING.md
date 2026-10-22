# Stable preview signing

The preview release channel is designed to update in place so Android keeps the app's private data, including the SQLite database and Telegram WebView session.

## One-time secret setup

This repository is public. **Never commit the keystore, its Base64 text, or its password to the repository.** The keystore was supplied separately to the project owner in the current ChatGPT conversation.

In the repository on GitHub:

1. Open **Settings → Secrets and variables → Actions**.
2. Select **New repository secret** and create:
   - `PREVIEW_KEYSTORE_BASE64`: paste the complete contents of the separately supplied `OSINT_PREVIEW_KEYSTORE_BASE64.txt` file. Paste the Base64 text only.
   - `PREVIEW_KEYSTORE_PASSWORD`: paste the exact password from the separately supplied `OSINT_PREVIEW_KEYSTORE_PASSWORD.txt` file, with no extra spaces or newline.
3. Keep these two secrets unchanged for all future preview releases. The alias is fixed as `osint-preview`; the key password is the same as the keystore password.
4. Open **Actions → Android CI → Run workflow → main**. After tests and packaging pass, download the artifact named `osint-workbench-v0.5.1-preview-apk` and extract `app-debug.apk`.

If either secret is missing or incorrect, CI may run tests/build checks but deliberately does not upload a downloadable APK. This prevents a temporary debug-signed APK from being mistaken for an updateable release.

## Important one-time migration from preview050

The earlier `com.cyberphantom.osintworkbench.preview050` APK was signed with a temporary GitHub-hosted debug key. Its private signing key was not retained, so Android cannot accept a replacement for that same package signed with a different key. The new stable channel therefore uses `com.cyberphantom.osintworkbench.preview051` and installs alongside the old app instead of removing it.

Keep the old app installed until the new channel is checked. Its existing session and data remain in the old installation; Android keeps the two package sandboxes separate, so the new channel needs its own Telegram Web login once. Export/import the app's JSON database backup if you also need to transfer OSINT workspace data. After this transition, later previews should update `preview051` in place as long as its application ID and the same stable signing key are preserved.

## Rules for every future release

- Keep the preview `applicationIdSuffix` as `.preview051`.
- Keep the two signing secrets unchanged and private.
- Increase `versionCode` and update `versionName` when publishing a newer build.
- Do not download/install an artifact from an Actions run that did not upload `osint-workbench-v0.5.1-preview-apk`.
