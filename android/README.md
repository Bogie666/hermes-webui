# Hermes for Android

A small native WebView client for an existing Hermes WebUI server. Requires Android 10+.
The Python backend and Hermes Agent continue running on your server; no API keys or agent runtime are bundled.
The existing responsive interface, login, session history and streaming are served directly from the configured HTTPS origin.

## Install and connect

1. Build the debug APK below, or download `hermes-android-debug` from a successful **Android APK** GitHub Actions run. Unzip the artifact and install `app-debug.apk` on your phone. Android may ask you to allow installation from the app opening the APK.
2. Launch **Hermes** and enter your WebUI HTTPS URL, including any reverse-proxy path.
3. Log in using the WebUI's own login. Cookies persist in Android's private WebView storage; Android backups are disabled.

For private servers, install/connect Tailscale on both devices and use the HTTPS address from Tailscale Serve. See [remote access](../docs/remote-access.md). Plain HTTP and invalid/self-signed certificates are deliberately unsupported. No server configuration is changed by installing this app.

Use the top-right connection menu to reload, change servers, or clear local login and drafts. Changing the URL clears client storage. Server conversations are unaffected. Reload is explicit and never automatically resends a message.

## Build

Open this `android/` directory in Android Studio, with JDK 17, SDK platform 35 and Build Tools 35.0.0 installed. Gradle 8.11.1 and Android Gradle Plugin 8.9.2 are pinned for reproducibility. With Gradle 8.11.1 installed:

```sh
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. CI runs the same checks and uploads an installable debug APK. Debug builds use a development signing key: separately built APKs may require uninstalling an old build. For ongoing distribution use a stable private release signing key in Android Studio; never commit signing keys/passwords. Play Store submission and release signing are separate steps.

## Device capabilities and limits

- System document picker supports single/multiple attachments without broad storage permissions. Camera capture is not added; select existing photos/files.
- Same-origin HTTPS downloads use Android Download Manager with the WebUI login cookie. Blob/data downloads are unsupported. Download Manager owns its redirects; deploy behind a trusted server/proxy.
- Microphone access grants only audio capture to the configured server after Android permission approval. Voice depends on the WebUI recording/transcription mode; browser-only Web Speech recognition may be unavailable in WebView. Keyboard dictation remains available.
- External HTTP(S) links ask before opening in the browser; other schemes and cross-origin embedded navigation are blocked. Cross-origin OAuth/SSO redirects are not supported in the app; use WebUI password login.
- No JavaScript-to-native bridge, TLS bypass, cleartext traffic, geolocation or camera permissions.
- Long tasks run on the server. Android can suspend or kill the client in the background. Reopen/reload and use WebUI session recovery; do not assume background streaming or notifications. Native push notifications, offline chat and native speech recognition are not included.
- Auth/local storage belongs to WebView; URL preference belongs to the Activity; pending file and audio callbacks are denied/canceled on navigation/teardown as appropriate. Device rotation retains the Activity; process recreation reloads the server and uses existing WebUI recovery. No chat state or cancellation endpoint is implemented in the shell.

## Validation

`ServerUrlTest` checks URL validation, proxy paths, spoofed hosts, credentials, scheme and port boundaries. It does not prove device behavior.

Before distributing, validate on an Android device/emulator against a disposable password-protected WebUI with isolated `HERMES_HOME` and `HERMES_WEBUI_STATE_DIR`:

- First launch, invalid URL, login, restart with saved login, change server and clear data.
- Send a chat, stream, switch sessions, rotate, background/resume, kill/reopen and recover a running session without duplicate sends.
- Attach one/multiple photos/documents and cancel the picker; deny/retry microphone permission and navigate during a request.
- Download an authenticated file, follow an external link, verify invalid TLS is rejected and an unavailable server can be reloaded.
- Verify keyboard, system bars, scrolling, TalkBack and menus at phone and tablet widths.

This is a new Android surface, so there is no previous Android screenshot. Device screenshots and the above integration checks are required before calling it release-ready.

## Architecture and rollback

The Android build is isolated in `android/`; the existing Python/vanilla-JS web app still needs no build step. Native Android APIs avoid adding a web bundler or changing origin/auth contracts. Remove this directory and `.github/workflows/android.yml` to roll back; there are no server migrations.
