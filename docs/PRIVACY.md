# Privacy

Open Caption is designed so that conversations and sounds stay on your devices.

## What happens to your audio

- The microphone is read on the phone while captions or sound alerts are on.
- Speech recognition (Whisper) and sound detection (YAMNet) run on the phone.
- Audio is held in memory for a few seconds and then discarded. **Audio is never written to disk and never sent over the network.**
- The app shows a notification while it is listening, and it stops when you turn both features off.

## What is stored

| Data | Stored where | How long | How to delete |
|------|--------------|----------|---------------|
| Caption text | Phone database (`opencaption.db`) | Until you delete it | **Clear captions** on the Home screen, or uninstall the app |
| Sound alerts (list shown in the Alerts tab) | Memory only | Until the app closes | Clear alerts, or close the app |
| Settings | Phone preferences | Until uninstall | Uninstall the app |

No accounts, analytics, or crash reporting are included.

## What is sent to the watch

Only text, sound names (for example "Car horn"), confidence scores, vibration pattern names, and display settings. The phone and watch talk over Bluetooth through the Wearable Data Layer, which Google's Wear OS platform provides.

## Network access

The app declares the internet permission, but it is only used if you turn on **Allow network captions** in Settings. That option is off by default. With it on, Android's network speech service may receive your speech. Keep it off if you need fully offline use.

## Accuracy and privacy of models

The models are downloaded once by you, from the sources in [MODELS.md](MODELS.md). Nothing in the app checks for updates.
