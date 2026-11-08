# Open Caption

Open Caption is free, open-source live captions and sound alerts for Deaf and hard-of-hearing people. It runs on an Android phone and on a Wear OS watch, and the watch connects to the phone over Bluetooth.

- **Live captions** of speech, offline, with Swahili and English mixed in the same sentence.
- **Sound alerts** for car horns, sirens, ambulances, smoke and fire alarms, doorbells, and baby crying. Alerts vibrate the phone, the watch, or both.
- **Glanceable watch**: captions, alerts, history, and start and stop buttons.
- **Private by design**: audio is analysed on the phone, held in memory, and never saved or uploaded.

> **Not a medical or emergency device.** Open Caption is an assistive aid. It can miss sounds and mis-hear speech. Do not rely on it alone for your safety.

Status: early but working. Tested on a Samsung Galaxy S20 Ultra (phone) and a Galaxy Watch4 (watch). See [Known limitations](docs/ROADMAP.md).

## Features

| Area | What it does |
|------|--------------|
| Captions | Speech is transcribed on the phone with Whisper (offline). Lines appear on the phone and the watch. Text size and fade time are adjustable. |
| Language | Mixed Swahili and English (auto-detect), English (Kenya), or Kiswahili (Kenya). |
| Sound alerts | YAMNet detects 20+ sounds, including traffic and emergency sounds. Each sound has its own vibration pattern. |
| Sound captions | Non-speech events such as `[Clapping]` or `[Laughter]` appear inline in the caption stream. |
| Watch | Captions, sound alerts, history with scroll-back, and toggles. The phone must stay on and within Bluetooth range. |
| History | Captions are stored in a local database on the phone, so you can scroll back on the watch. |
| Privacy | No accounts, no cloud, no analytics. See [docs/PRIVACY.md](docs/PRIVACY.md). |

## How it works

```
 Phone                                                   Watch
 ─────────────────────────────────────────────────       ───────────────────────
 Microphone ─► MicStream (one shared stream)
                 ├─► Silero VAD ─► Whisper ─► captions ──► Wearable Data Layer ─► captions screen
                 └─► YAMNet ─► alert rules ───────────────► (Bluetooth)          ─► vibration + alert card
```

Read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full design and [docs/PROTOCOL.md](docs/PROTOCOL.md) for the phone-watch messages.

## Quick start

1. Install Android Studio and JDK 17. Details: [docs/SETUP.md](docs/SETUP.md).
2. Build the apps:
   ```sh
   ./gradlew :phone:assembleDebug :wear:assembleDebug
   ```
3. Install the phone app and open it once:
   ```sh
   adb install -r phone/build/outputs/apk/debug/phone-debug.apk
   ```
4. Copy the offline speech models to the phone. Without them, captions fall back to Android's built-in speech recognizer:
   ```sh
   scripts/install-models.sh path/to/models
   ```
   Model sources and sizes: [docs/MODELS.md](docs/MODELS.md).
5. Pair the watch with the phone in the Galaxy Wearable app. The phone build includes the watch app. If it does not appear on the watch, install it with adb: [docs/SETUP.md](docs/SETUP.md#install-the-watch-app).

## Repository layout

```
phone/    Android app: capture, captions, sound alerts, settings, history
wear/     Wear OS app: captions, alerts, controls
shared/   Message formats and sound label table shared by both apps (with unit tests)
scripts/  Helper scripts (model installation)
docs/     Documentation, including the original feasibility study
```

## Documentation

- [Setup and building](docs/SETUP.md), including watch pairing and wireless adb
- [Architecture](docs/ARCHITECTURE.md): components, threads, data flow, and where to change things
- [Protocol](docs/PROTOCOL.md): phone-watch messages
- [Models](docs/MODELS.md): every model, its source, its license, and how to swap it
- [Troubleshooting](docs/TROUBLESHOOTING.md)
- [Privacy](docs/PRIVACY.md)
- [Roadmap and known limitations](docs/ROADMAP.md)
- [Contributing](CONTRIBUTING.md)
- [Feasibility study](docs/research/feasibility-study.md): the original research behind the design

## Acknowledgements

Open Caption builds on these open projects:

- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Apache-2.0): on-device speech recognition runtime
- [Whisper](https://github.com/openai/whisper) (MIT): speech recognition model
- [Silero VAD](https://github.com/snakers4/silero-vad) (MIT): voice activity detection
- [GTCRN](https://github.com/Xiaobin-Rong/gtcrn): speech noise reduction model
- [YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) (Apache-2.0): sound classification, trained on AudioSet
- [MediaPipe Tasks](https://developers.google.com/mediapipe) (Apache-2.0): audio classification runtime
- [Jetpack Compose](https://developer.android.com/jetpack/compose) and [Wear Compose](https://developer.android.com/training/wearables/compose) (Apache-2.0)

## License

MIT. See [LICENSE](LICENSE).

## Author

**Brian Keya**
- Website: [briankeya.tech](https://briankeya.tech)
- Email: [brianosiemokeya@gmail.com](mailto:brianosiemokeya@gmail.com)
- GitHub: [github.com/keya254](https://github.com/keya254)

Feedback, bug reports, and help with Swahili speech, Deaf user testing, and new sounds are very welcome.
