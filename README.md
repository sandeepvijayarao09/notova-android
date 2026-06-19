# notova-android

Notova for Android — on-device AI voice capture & notes (Kotlin/Jetpack Compose). Record from any
mic or audio file; transcribe and summarize **fully on-device**; export to your apps.

The backend exists only for accounts, OAuth integration brokering, metadata sync, and billing — it
**never** performs AI compute. Transcription (Whisper) and summarization (Gemma 3n E4B) run locally.
Both are currently **stub implementations** behind interfaces so the real models can drop in later.

## Module map

| Module | Type | Responsibility |
| --- | --- | --- |
| `:app` | Android application (`com.notova.app`) | `@HiltAndroidApp` `NotovaApp`, `MainActivity` (Compose + Navigation: Record / Notes / Settings), `ProcessRecordingWorker` (WorkManager). Depends on every module. |
| `:core` | Android library (`com.notova.core`) | Pure domain: models, the on-device pipeline interfaces (`AudioSource`, `Transcriber`, `Summarizer`, `IntegrationExporter`), their stub impls, and `PipelineUseCase`. Hilt `PipelineModule` binds the stubs. |
| `:data` | Android library (`com.notova.data`) | Room database (`Recording` + `Summary` entities/DAOs), `RecordingRepository` impl, DataStore preferences, Hilt modules. |
| `:design` | Android library (`com.notova.design`) | Compose `NotovaTheme`, typography, shared components. |
| `:integrations` | Android library (`com.notova.integrations`) | Retrofit `NotovaBackendApi` for the `/v1` contract + DTOs, `IntegrationExporter` impl, networking Hilt module. |
| `:feature:record` | Android library (`com.notova.feature.record`) | `RecordScreen` + `RecordViewModel`; `MediaRecorderAudioSource` (MediaRecorder capture, Bluetooth SCO routing, file import via Storage Access Framework `OpenDocument`). |
| `:feature:notes` | Android library (`com.notova.feature.notes`) | `NotesListScreen` / `NoteDetailScreen` + ViewModels. |

Supporting files: `gradle/libs.versions.toml` (version catalog), `config/detekt.yml`, `.editorconfig`,
`.github/workflows/android.yml` (temurin JDK 17, `./gradlew assembleDebug`).

## Build instructions

**JDK 17 is required.** A newer JDK on `PATH` will break AGP/Kotlin — always pin `JAVA_HOME`.

```bash
# Point local.properties at your SDK (gitignored):
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties

# Build the debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug

# Unit tests
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew testDebugUnitTest

# Static analysis
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew detekt ktlintCheck
```

### Toolchain versions

| Tool | Version |
| --- | --- |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 (Compose compiler = built-in `kotlin.plugin.compose`) |
| Gradle (wrapper) | 8.13 |
| compileSdk / targetSdk | 35 |
| minSdk | 26 |
| JDK | 17 |

Stack: Jetpack Compose (BOM) + Material3, Navigation-Compose, Hilt, Coroutines/Flow, Room,
DataStore, Retrofit + OkHttp + kotlinx-serialization, WorkManager, KSP. Lint via ktlint + detekt.

## Where Whisper / Gemma plug in

The pipeline is interface-driven so the real models swap in with zero changes to callers:

- **Whisper (transcription)** — implement `com.notova.core.transcribe.Transcriber` (e.g.
  `WhisperTranscriber` wrapping whisper.cpp / LiteRT) and rebind it in
  `com.notova.core.di.PipelineModule` in place of `StubTranscriber`.
- **Gemma 3n E4B (summarization)** — implement `com.notova.core.summarize.Summarizer` (e.g.
  `GemmaSummarizer` via MediaPipe LLM Inference / LiteRT) and rebind it in `PipelineModule` in
  place of `StubSummarizer`.

`PipelineUseCase` composes `Transcriber` + `Summarizer` (audio file → transcript → summary). The
Record flow (`RecordViewModel`) and the background `ProcessRecordingWorker` both depend only on
`PipelineUseCase`, so dropping in real models requires editing only the Hilt bindings.

### Backend `/v1` contract

`NotovaBackendApi` targets: auth (`register` / `login` / `refresh` / `me`), integrations (list,
`:provider/connect`, `callback`, `:provider/export`, `disconnect`), sync (`recordings` GET/PUT),
and billing (`subscription`, `checkout`). OAuth returns via the `notova://` deep link scheme
(registered in the app manifest).

## License

Apache-2.0. See `LICENSE` and `NOTICE`. On-device AI models carry their own separate licenses and
are not bundled in this scaffold.
