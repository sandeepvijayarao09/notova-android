# Changelog

All notable changes to Notova for Android are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-07-08

Initial public release of the Android app (`com.notova.app`).

### Added
- **Audio capture** from the microphone, a Bluetooth microphone (SCO routing),
  or an imported audio file via the Storage Access Framework.
- **On-device transcription** via Android `SpeechRecognizer`, or an on-device
  Gemma model with audio support (LiteRT-LM) when installed.
- **On-device summarization** producing a summary and action items via Gemini
  Nano (ML Kit GenAI) or an on-device Gemma model (LiteRT-LM). A runtime
  resolver picks the best available engine and falls back gracefully — down to a
  small built-in summarizer — so the pipeline always completes offline.
- **On-device model management**: import a model file (SAF) or download one
  in-app; the active engine is shown in Settings.
- **Background processing**: long recordings/imports are processed durably via
  WorkManager (`ProcessRecordingWorker`) so work survives navigation.
- **Local notifications**: a persistent "Recording…" notification backed by a
  microphone foreground service, an ongoing "Processing recording…"
  notification during background processing, and a "Note ready" completion
  notification. All local — nothing is sent to a server and there is no push.
- **Notes**: list and detail screens (summary, action items, transcript) backed
  by Room.
- **Optional account** (email/password) with tokens in EncryptedSharedPreferences;
  bearer wiring with refresh-once on 401.
- **Continue without an account** guest mode — full on-device recording,
  transcription, and summarization with zero setup.
- **Integrations** screen with in-app OAuth (`notova://` deep-link callback).
  **Notion export is functional**; Google, Slack, and Salesforce are scaffolded.
- **Store readiness**: R8 minification + resource shrinking with keep rules,
  release signing via `keystore.properties`, adaptive launcher icon, ABI splits,
  and `versionCode = 1` / `versionName = "1.0.0"`.
- **Launch docs**: Play Store listing copy (`store/PLAY_STORE.md`), support page
  (`SUPPORT.md`), and hostable privacy policy (`docs/privacy-policy.md`).

### Privacy
- Audio, transcripts, and summaries are created and stored on-device and are
  never uploaded. No ads, no analytics or tracking SDKs, no third-party tracking.
  `POST_NOTIFICATIONS` is used only for local notifications. Only the account
  email and note metadata are ever processed server-side, and only when signed
  in.

[1.0.0]: https://github.com/sandeepvijayarao09/notova-android/releases/tag/v1.0.0
