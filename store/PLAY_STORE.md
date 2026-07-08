# Notova — Google Play listing

Copy-ready metadata for the Play Console entry for `com.notova.app`.

---

## Store listing fields

- **App title (≤30):** `Notova: AI Voice Notes` (22 chars)
- **Short description (≤80):**

```
Record, transcribe & summarize voice notes on-device. Private by design.
```

(72 chars.)

- **Category:** Productivity
- **Tags:** Productivity, Tools
- **Contact email:** `<FILL IN: support email>`
- **Website (optional):** `<FILL IN: marketing site URL>`
- **Privacy Policy URL (required):** `<FILL IN: privacy policy URL>` (host `docs/privacy-policy.md`)

### Full description (≤4000)

```
Notova turns your voice into notes — and keeps every word on your device.

Record a meeting, a lecture, an interview, or a quick idea. Notova transcribes
it and writes a clean summary with action items using on-device AI. Your audio,
transcripts, and summaries are created and stored on your device and are never
uploaded.

CAPTURE FROM ANY SOURCE
• The phone microphone
• A paired Bluetooth microphone or headset
• An audio file you already have — import it and Notova does the rest, even for
  long recordings that keep processing in the background

ON-DEVICE TRANSCRIPTION AND SUMMARIES
• Transcription runs on-device (Android SpeechRecognizer, or an on-device Gemma
  model with audio support when installed).
• Summaries and action items are generated on-device by Gemini Nano on
  supported devices, or by an on-device Gemma model you import or download.
• No internet connection is required to record, transcribe, or summarize.

PRIVATE BY DESIGN
• Audio, transcripts, and summaries stay on your device.
• No ads. No analytics or tracking SDKs. No third-party tracking.
• The optional Notova account only stores your email and syncs note METADATA
  (title, duration, timestamps) — never your audio or text.

USE IT WITH ZERO SETUP
• Tap "Continue without an account" and start recording immediately. No sign-up,
  no server, no waiting.
• Create an account later for cross-device metadata sync and integrations.

KNOW WHEN YOUR NOTE IS READY
• While recording, a "Recording…" notification keeps the microphone capture
  running reliably in the foreground.
• Long recordings finish processing in the background with a "Processing
  recording…" notification, then a "Note ready" notification when done.
• These are on-device notifications only. Nothing is sent to a server and there
  is no push messaging.

SEND NOTES WHERE YOU WORK
• Export a finished note to Notion today. More export targets (Google, Slack,
  Salesforce) are on the way.

Notova is open source (Apache-2.0).

Note on summaries: on a device without an on-device model — or before you
install one — Notova produces a short built-in summary so the app is always
usable. Install or download a model for richer summaries.
```

### v1.0.0 release notes (What's new, ≤500)

```
Notova 1.0. Record from the mic, a Bluetooth mic, or an imported audio file, then transcribe and summarize entirely on-device. Export finished notes to Notion. Use it with no account via "Continue without an account", or sign in for cross-device metadata sync. Recording and background processing now show local notifications, so you always know when a note is ready. No ads, no analytics, no tracking.
```

(402 chars.)

---

## Data safety form

Notova performs all AI on-device and never uploads audio, transcripts, or
summaries. Complete the form as follows.

**Does your app collect or share any of the required user data types?**
Yes — only the account email, and only when the user signs in.

**Is all of the user data collected by your app encrypted in transit?** Yes
(HTTPS/TLS to the backend).

**Do you provide a way for users to request that their data be deleted?** Yes —
in-app account deletion and/or a contact request (see privacy policy).

### Data collected / shared

| Data type | Collected | Shared | Purpose | Optional? |
| --- | --- | --- | --- | --- |
| Personal info → Email address | Yes | No | Account management, App functionality (authentication, cross-device metadata sync) | Yes — only if the user creates an account |

- **Used for tracking / advertising:** No.
- **Data processing is on-device where possible:** Note that audio, transcripts,
  and summaries are processed on-device and are **not collected**.

### Data explicitly NOT collected and NOT shared

- Audio recordings — on-device only
- Transcripts — on-device only
- AI summaries and action items — on-device only
- Location, contacts, photos, files, messages, health, financial info
- Device or other identifiers, advertising ID
- App activity / analytics, crash logs, diagnostics

There are **no** ads, **no** analytics SDKs, and **no** third-party tracking.

> This mirrors the release guide (`RELEASE.md`) Data-safety mapping.

---

## Content rating questionnaire (IARC)

Target rating: **Everyone**. Notova is a productivity/utility app with no
objectionable content.

- **App category:** Utility, Productivity, Communication, or Other.
- Violence: **No**
- Sexuality / nudity: **No**
- Profanity / crude humor: **No**
- Controlled substances (drugs, alcohol, tobacco): **No**
- Gambling (simulated or real money): **No**
- Fear / horror content: **No**
- User-generated content shared with others: **No** (notes stay on-device; the
  only export is a note the user explicitly sends to their own connected
  service)
- Does the app share the user's location? **No**
- Does the app allow users to interact or exchange content? **No**
- Digital purchases: billing is present as a stub for a future Pro tier; declare
  in-app purchases only if/when checkout is live.

Resulting rating: **Everyone / PEGI 3 / rated for all ages.**

---

## Graphics assets checklist

- **App icon:** 512×512 PNG (32-bit, with alpha)
- **Feature graphic:** 1024×500 PNG/JPG (required)
- **Phone screenshots:** 2–8, 16:9 or 9:16 (min 320px)
- **7" and 10" tablet screenshots:** if tablet support is declared

Suggested shots: Record screen mid-recording (with the "Recording…"
notification visible), Notes list, Note detail (summary + action items +
transcript), the "Continue without an account" button, Settings showing the
active on-device engine.

See also: [`RELEASE.md`](../RELEASE.md), [`../LAUNCH.md`](../LAUNCH.md).
