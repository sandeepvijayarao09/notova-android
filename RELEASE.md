# Notova for Android — Play Store release guide

This documents how to ship `com.notova.app` to Google Play, and what is already configured in
the repo vs. what requires your Google Play account.

## Already configured in the repo

- **Release minification**: R8 with `isMinifyEnabled = true` + `isShrinkResources = true`
  (`app/build.gradle.kts`), keep rules for the JNI/reflection surfaces (LiteRT‑LM, MediaPipe,
  ML Kit), kotlinx.serialization, and Retrofit/OkHttp in `app/proguard-rules.pro`.
- **Release signing**: driven by a gitignored `keystore.properties` at the repo root
  (template: `keystore.properties.example`). Without it, release builds fall back to the debug
  key so CI/local `assembleRelease` still succeeds.
- **Versioning**: `versionCode = 1`, `versionName = "1.0.0"`. Bump `versionCode` on every upload.
- **Adaptive launcher icon**: `app/src/main/res/mipmap-anydpi-v26` (+ foreground drawable).
- **Permissions** (all justified): `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH_CONNECT`,
  `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE(_MICROPHONE)`.
- **Size**: ABI splits (arm64‑v8a, x86_64) for APKs; for Play, the App Bundle handles ABI
  splitting automatically.

## Build artifacts

```bash
# App Bundle (.aab) — the format Play requires for new apps:
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:bundleRelease
# → app/build/outputs/bundle/release/app-release.aab

# Or signed APKs (per-ABI), e.g. for sideload/QA:
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleRelease
```

## One-time signing setup

```bash
keytool -genkeypair -v -keystore notova-upload.jks -alias notova-upload \
  -keyalg RSA -keysize 2048 -validity 10000
cp keystore.properties.example keystore.properties   # then fill in paths + passwords
```
Enroll in **Play App Signing** so Google holds the app signing key; the key above is then only
your *upload* key (rotatable if lost).

## Requires your Google Play account (cannot be done from the repo)

1. **Google Play Developer account** ($25 one-time) and a new app entry for `com.notova.app`.
2. **Upload** the `.aab` to an internal/closed testing track first.
3. **Store listing**: title, short (80 char) + full (4000 char) descriptions, app icon (512×512),
   feature graphic (1024×500), phone + tablet screenshots.
4. **Content rating** questionnaire.
5. **Data safety** form — use this mapping (matches the app's actual behavior):
   - *Audio recordings, transcripts, AI summaries*: **not collected / not shared** — processed
     and stored on-device.
   - *Email address*: **collected**, for account functionality, **not shared**, not used for
     tracking. (Only when the user signs in; the app also offers "Continue without an account".)
   - No advertising, no analytics SDKs, no third-party tracking.
6. **Privacy policy URL** (required) — host one (see template in `PRIVACY.md`).
7. **App access**: Notova works without an account (the Record screen has *Continue without an
   account*). If reviewers want the signed-in path, provide a demo account in review notes.
8. **Target API level**: targetSdk 35 (current). Keep current each year.

## Pre-launch gate

Because release builds are minified, **smoke-test a signed release build on a physical device**
(record → transcribe → summarize → import) before promoting to production — R8 keep-rule gaps
only surface at runtime, not at build time.
