# Roadmap and known limitations

This is an early, working prototype. This page lists what it does not do yet, and where help would make the biggest difference.

## Known limitations

**Testing**
- Tested only on a Samsung Galaxy S20 Ultra (phone) and a Galaxy Watch4 (watch).
- Accuracy has not been measured with Kenyan English, Kiswahili, or mixed speech in noisy places. Build a small labelled test set before trusting the captions for important use.
- Sound detection has not been measured on real streets. Test before relying on it, and report false alarms and misses.
- No automated tests for the apps' screens or the audio pipeline. Only the shared message format has tests.

**Captions**
- Offline captions appear after each phrase is decoded, so there is a delay of about a phrase plus the decode time. There are no live partial words in the offline engine.
- The offline model must be copied to the phone by hand. There is no in-app download.
- Without the offline model, captions use Android's recognizer, which may send audio to Google when no on-device language is installed.
- Captions and the Android fallback both want the microphone. When the fallback is used, sound alerts may not hear sound while captions run.

**Sound alerts**
- Sensitivity is one setting for all sounds. Per-sound sensitivity is not available.
- Alerts are not validated as a safety system, and they are not certified for any purpose.
- A sound must be recognised in two windows in a row. Very short sounds can be missed.
- The phone must be on and listening for alerts to reach the watch.

**Watch**
- The watch cannot run without the phone. There is no standalone mode.
- The history on the watch is a window of recent lines. Older lines are requested from the phone.
- The watch app uses a phone-first design. Battery impact on the watch has not been measured.

**Phone and watch link**
- Messages are best effort. Gaps are filled when the watch reconnects, but only up to the phone's history.
- There is no version field in the message format, so the phone and watch must be built from the same commit.

**Platform**
- Android and Wear OS only. There is no iOS or web version.
- Only debug builds are documented. There is no signed release build or store listing.
- Accessibility of the app itself (for example TalkBack labels and contrast) has not been audited.

**Data**
- Caption history grows without limit until you clear it.

## Ideas, roughly in order of value

1. **Live partial captions** on the offline engine, so words appear while a phrase is still being spoken.
2. **Streaming speech model** (sherpa-onnx online transducer) for seamless captions, once a model with good Swahili results is found.
3. **Measured accuracy**: a public test set of Kenyan English, Kiswahili, and mixed speech, plus word error rate reporting.
4. **Per-sound sensitivity** and a "test this sound" button in Settings.
5. **In-app model download** with checksums, so users do not need a computer.
6. **Sound direction** (the feasibility study explains why this is hard on a watch).
7. **Watch tile and complication** for one-tap start.
8. **TalkBack and large-text audit** of both apps.
9. **Tests**: instrumentation tests for the caption and alert pipeline, and screenshot tests for the screens.

## Good first contributions

- Try the app with your own speech and sounds, and file issues with the decode times and what went wrong.
- Add a sound label that is missing from `SoundLabels`, with its vibration pattern.
- Improve these docs, especially setup steps for other phone and watch models.
- Write a unit test for the caption cleaner in `LocalSpeechEngine.clean`. It is private today, so moving it to a small object would make that possible.
