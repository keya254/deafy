# Architecture

This document explains how Open Caption is put together and where to change things. Read the [README](../README.md) first for an overview, and [PROTOCOL.md](PROTOCOL.md) for the phone-watch messages.

## Modules

| Module | Package | Role |
|--------|---------|------|
| `phone` | `tech.briankeya.opencaption.phone` | Android app. Does all audio work (captions and sound detection), stores history, and holds the settings. |
| `wear` | `tech.briankeya.opencaption.wear` | Wear OS app. Shows captions and alerts, vibrates, and sends start and stop commands to the phone. |
| `shared` | `tech.briankeya.opencaption.shared` | Message classes, the sound label table, and vibration patterns. Used by both apps. Has JVM unit tests. |

The phone is the brain. The watch is a display and a remote control. Audio never leaves the phone.

## Phone components

```
OpenCaptionApp (startup)
 ├─ Settings          SharedPreferences: captions, sound, vibration
 ├─ History           Room database (opencaption.db): caption history
 └─ WatchLink         Wearable Data Layer: finds the watch, sends messages

MainActivity (Compose UI: Home, Alerts, Settings)
 └─ asks ListenService to start or stop captions and/or sound alerts

ListenService (foreground service, type "microphone")
 ├─ MicStream         one AudioRecord, shared by everything below
 ├─ LocalSpeechEngine offline captions (Whisper via sherpa-onnx)   [preferred]
 │    or CaptionEngine  Android SpeechRecognizer                   [fallback]
 └─ SoundEngine       sound detection (YAMNet via MediaPipe)

PhoneCommandService  receives watch commands and history requests
Alerter              turns a sound alert into a phone notification, phone vibration, and a watch message
```

### Startup

`OpenCaptionApp.onCreate` initialises `Settings`, `History`, and `WatchLink`, then loads the latest captions into `AppState` in the background. Nothing listens to the microphone until the user turns something on.

### Starting and stopping listening

Turning on captions or sound alerts (on the phone or the watch) calls `ListenService.request(captions, sound)`. The service:

1. Calls `startForeground` straight away. Android requires this within seconds.
2. Starts `LocalSpeechEngine` if the offline model is installed, otherwise `CaptionEngine`.
3. Starts `SoundEngine` if sound alerts are on, or if any sound is set to appear as a caption tag.
4. Reports the new state to the UI (`AppState`) and to the watch (`/opencaption/status`).
5. Stops itself when neither feature is on.

Turning one feature off leaves the other running. When the user changes a setting that an engine reads at start-up (language, labels, sensitivity), the app sends a `reload` request, and the service restarts the running engines so the change takes effect.

## The shared microphone

`MicStream` is the single owner of the microphone:

- Opens one `AudioRecord` (voice-recognition source, 16 kHz, mono or multi-mic downmixed to mono) when the first listener subscribes.
- Reads 100 ms chunks on its own thread and passes each chunk to every subscriber.
- Closes the microphone when the last subscriber leaves.
- Subscribers receive the same array, so they must copy it before changing it. `LocalSpeechEngine` copies before applying gain.
- Dispatch happens under a lock. Once `release()` returns, that listener is never called again, which makes it safe to free resources afterwards.

Earlier versions opened a second microphone for sound alerts. Android does not reliably share a microphone between two recorders in one app, which caused alerts to go silent while captions were on.

## Captions

Offline path (`LocalSpeechEngine`):

```
MicStream (100 ms chunks)
  → copy, apply gain (AGC up to the user's boost)
  → Silero VAD: finds speech; a 0.4 s pause ends a phrase; phrases are cut at 4 s
  → for each phrase, on a single decode thread:
       optional GTCRN noise reduction
       high-pass filter and peak normalisation
       Whisper decode (greedy search, language auto-detect in "Mixed" mode)
       clean(): drop hallucinations, repeated words, non-Latin scripts, Spanish-only output
  → AppState.addCaption → History (database) and the watch
```

Things that affect speed and quality, all in `LocalSpeechEngine`:

- `maxSpeechDuration` (4 s): shorter cuts show text sooner. Whisper always processes a fixed 30-second window, so each cut costs roughly one full decode.
- `minSilenceDuration` (0.4 s): a shorter pause ends a phrase sooner, but can split words.
- `WHISPER_THREADS`: CPU threads used for decoding, 2 to 4 depending on the phone.
- Decode time is logged at debug level under the `LocalSpeech` tag. Use it to judge whether a phone keeps up.

Fallback path (`CaptionEngine`): Android's `SpeechRecognizer` in on-device mode, or network mode if the user allows it. It opens its own microphone, so sound alerts may not hear sound while it runs. This only happens when the offline model is not installed.

## Sound alerts

```
MicStream (100 ms chunks)
  → 15 600-sample rolling window (0.975 s at 16 kHz), refreshed every 500 ms
  → YAMNet (MediaPipe AudioClassifier): scores all 521 AudioSet classes
  → SoundEngine.onResult:
       keep labels that are enabled and score ≥ sensitivity (default 0.3)
       require the label in 2 consecutive windows
       cooldown: 10 s per alert label, 4 s per caption tag
  → Alerter.raise: history, phone vibration, notification, watch alert
```

Labels are defined once in `shared/.../Protocol.kt` (`SoundLabels`):

- `ALL` maps each YAMNet label to the short name shown on the watch.
- `DEFAULT_ENABLED` lists the sounds on at first run.
- `DEFAULT_PATTERN` gives each sound its vibration pattern.
- `EVENTS` lists non-speech sounds shown inline in captions (for example `[Clapping]`).

To add a sound, add its exact YAMNet name (it must appear in the model's label list) to `ALL`, and to `DEFAULT_PATTERN` and `DEFAULT_ENABLED` if needed. The unit tests check that defaults only refer to known labels.

## Settings and migrations

`Settings` wraps SharedPreferences. Keys are versioned when a default changes (`sound_labels_v2`, `sound_threshold_v2`), so existing installs pick up new defaults without a manual reset. Do this for any default you change, and say so in the commit message.

## Watch

- `PhoneDataService` receives messages from the phone and updates `WearState`.
- `PhoneLink` tracks whether the phone is reachable and sends commands to it. On reconnect it asks the phone for captions missed while out of range.
- `MainActivity` renders the captions screen (rolling lines, a partial line, a status note), the history screen (scroll up to load older pages), the alert card, and two toggles.
- Haptic alerts use the same `VibePattern` waveforms as the phone.

The watch keeps only the most recent lines in memory. Older captions are requested from the phone database page by page.

## Persistence

| Data | Where | Notes |
|------|-------|-------|
| Settings | SharedPreferences `opencaption_settings` | Cleared when the app is uninstalled |
| Caption history | Room database `opencaption.db`, table `captions` | Lines only. No audio is stored. |
| Offline models | App external files folder, `models/` | Copied with `scripts/install-models.sh` |

## Threads

| Thread | Owner | Work |
|--------|-------|------|
| `opencaption-mic` | `MicStream` | Reads the microphone and calls listeners |
| `opencaption-whisper` | `LocalSpeechEngine` | Denoise and decode (single thread, so phrases are decoded in order) |
| `opencaption-sound` | `SoundEngine` | Sends a window to YAMNet every 500 ms |
| MediaPipe result thread | `SoundEngine` | Calls `onResult` |
| Main thread | Android | UI, and posting results from the background threads |

## Where to change things

| To change | Look at |
|-----------|---------|
| Caption timing or quality | `LocalSpeechEngine.loadModels`, `onAudio`, `clean` |
| Which sounds alert, and sensitivity | `SoundLabels` in `shared`, `SoundEngine.onResult`, `Settings.threshold` |
| Vibration patterns | `VibePattern` in `shared`, `SoundLabels.DEFAULT_PATTERN` |
| Phone-watch messages | `shared/.../Protocol.kt`, `docs/PROTOCOL.md` |
| Watch screens | `wear/.../MainActivity.kt` |
| Phone screens | `phone/.../ui/` |
| Microphone settings | `MicStream.openRecorder` |

For the reasoning behind these choices, see the [feasibility study](research/feasibility-study.md).
