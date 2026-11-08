# Galaxy Watch4 assistive app: feasibility and product direction

## Bottom line

A Galaxy Watch4 can be the user's always-available alert and glance surface, but it should not be the only sensing or inference device for the first version. The strongest design is a **hybrid Wear OS + Android phone system**:

- The **watch** shows short caption snippets, sound labels, confidence, and a large directional arrow; it vibrates with distinct patterns and provides start/stop, acknowledge, save, and emergency actions.
- The **phone** performs continuous microphone capture, speech recognition, sound-event classification, optional camera-based hazard detection, history, settings, and model updates.
- The **AI for Accessibility Toolkit** supplies the portable accessibility profile, consent model, onboarding language, and mobile rendering conventions. It does not itself provide a live-caption engine, sound detector, or direction-of-arrival estimator.

This approach is also safer. A wrist device can alert quickly, but a small screen and a single body-worn microphone are poor foundations for dependable, continuous speech and spatial-audio interpretation.

## What the referenced toolkit can contribute

The repository is an Apache-2.0 research prototype, explicitly pre-alpha, and says it is not itself assistive technology. Its useful role here is as a personalization and privacy layer rather than as the perception stack. It supports a portable `AbilityModel`, skills, adaptations, consent-gated profile changes, and platform renderers. Its mobile renderer translates needs such as text scale, dark theme, reduced motion, touch target size, captions, and related settings into mobile-friendly values. [1]

The cloned repository contains directly relevant building blocks:

- `toolkit/surfaces/mobile.js` for converting the user's profile into mobile settings.
- `toolkit/core`, `toolkit/blocks/profile.js`, and the Librarian/Datastore for local profile and preference management.
- The Consent port and cross-app sync/grant code for explicit sharing and user approval.
- `tools/adapters/show-captions.js`, `tools/adapters/generate-captions.js`, `tools/core-adapters/captions-on.js`, the `captions` need, and the built-in `quiet-video` skill. These are useful patterns and vocabulary, but they target web/video media. The repository itself describes generated captions as cloud-AI, chunked, and not truly live; that implementation should not be copied unchanged for an on-the-go wearable. [1]
- The existing XR bridge is a useful architectural precedent: it maps a shared accessibility profile into a non-web surface and explicitly handles captions and pacing. A Wear OS host could add a `wear.js` surface or use the mobile surface plus watch-specific settings.

Recommended new toolkit needs for this product are `liveCaptions`, `soundAlerts`, `hazardPriority`, `hapticLanguage`, `directionalAlerts`, `captionLanguage`, `captionFontScale`, `privacyMode`, and `offlineOnly`. Keep inferred preferences consent-gated. Do not store raw microphone audio by default; store only user-approved event summaries such as “horn detected, 3 o'clock, high confidence.”

## What the Watch4 can use

Samsung's Watch4 generation is a Wear OS watch with a color AMOLED display, microphone, speaker, vibration motor, GPS, Bluetooth/Wi-Fi connectivity, and optional LTE depending on the exact model. Public specifications also identify 1.5 GB RAM and 16 GB storage, with accelerometer, gyroscope, geomagnetic/compass, barometer, optical heart-rate, electrical heart, and bioelectrical-impedance sensors. Confirm the exact 40/44 mm and Bluetooth/LTE variant on the user's watch before sizing models and battery targets. Samsung's support material confirms GPS and Wear OS operation. [2] [3]

These capabilities map well to:

- **Glanceable display:** short live-caption phrases, speaker labels, sound type, confidence, direction, and status such as “phone connected” or “offline mode.”
- **Haptics:** distinct patterns for speech, horn, siren, alarm, name mentioned, doorbell, baby cry, and low-confidence events. Android provides predefined effects and waveform patterns through `VibrationEffect`; vibration requires the `VIBRATE` permission, and richer effects must be capability-checked. [6]
- **Orientation:** use the watch's compass/geomagnetic sensor plus gyroscope to express a phone-estimated sound bearing relative to the user's current heading. The arrow should be labelled “approximate” unless the system has a reliable multi-microphone bearing.
- **Motion and context:** accelerometer/gyroscope can suppress duplicate alerts while the wrist is moving, detect that the user is walking or cycling, and support a “do not disturb while sleeping” or “walking mode.” GPS can provide context and optional emergency-location sharing, but it is not needed for sound detection.
- **Connectivity:** use the Wearable Data Layer for watch-phone messages and synchronization when the watch is paired to an Android phone. Google documents the Data Layer as the supported communication channel for Wear OS and paired Android devices; Bluetooth is direct, while some transfers may route through Google infrastructure when Bluetooth is unavailable. [5]
- **Standalone fallback:** the watch can still run a reduced offline mode if the selected model fits and the watch variant has suitable connectivity, but this should be a fallback rather than the initial architecture.

## Live captions: what is practical

A caption screen on the watch is feasible for **short rolling snippets**, not as a full transcript page. A good watch UI is two or three lines of large text, high contrast, automatic scroll, a “repeat last phrase” action, language/status, and a clear indicator when recognition is stale or uncertain. The phone should maintain the transcript and the watch should receive the most recent sentence or two.

Android's `SpeechRecognizer` requires `RECORD_AUDIO`, can provide partial results, and exposes an on-device recognizer when available. However, Android explicitly warns that the general recognizer may stream audio to remote servers and is not intended for continuous recognition because of battery and bandwidth cost. [7] Therefore:

1. Prefer an on-device recognizer when available and the language model is downloaded.
2. Otherwise run recognition on the phone with a clearly disclosed network mode.
3. Avoid using the watch's general `SpeechRecognizer` as the primary always-on engine.
4. Use a user-started foreground service with an ongoing activity for a live session. Wear OS guidance recommends a foreground service for user-initiated long-running work and an Ongoing Activity so the user can return to it. [4]
5. Keep raw audio ephemeral and make recording state unmistakable.

The Watch4's small display also means captions should be optimized for **meaning per glance**. Add punctuation, speaker-change markers, important-number emphasis, and a one-tap “send transcript to phone/history” action rather than attempting a dense phone-sized transcript on the wrist.

## Sound alerts and hazard detection

For horns, sirens, alarms, doorbells, barking, glass breaking, smoke alarms, and similar events, use an on-device audio classifier. Google's MediaPipe Audio Classifier supports live audio-stream classification on Android and can use a packaged compatible model, an allow/deny list, confidence thresholds, and a result listener. [8] A model such as YAMNet is a useful starting point for broad sound categories, but it should be evaluated and then fine-tuned or replaced for the sounds that matter to the target community.

Use a three-stage pipeline:

1. **Detect:** classify a rolling audio window on the phone.
2. **Decide:** smooth results over time, apply confidence and cooldown thresholds, personalize the alert list, and combine sound type with walking/cycling context.
3. **Alert:** send a compact event to the watch, vibrate, show the label and confidence, and show a directional cue if a bearing is available.

Do not present model output as certainty. Use wording such as “possible car horn” and let the person choose sensitivity, sounds, quiet hours, and whether repeated events are grouped.

## Direction arrow: important limitation

The requested arrow is product-useful but cannot be promised from the Watch4 alone. Sound direction normally requires timing or level differences between multiple microphones, or a trained model with strong device-specific calibration. A body-worn watch has a very small microphone baseline and its orientation changes constantly. A single-watch-microphone solution may detect “sound present” and perhaps learn rough wrist-orientation correlations, but it will not provide dependable 360-degree direction in varied streets, echoes, wind, or multiple sources.

Recommended options, in order:

- **Best first version:** run direction estimation on the phone using its microphone array, then express the result on the watch relative to the watch/phone heading. Validate with real users and label it approximate.
- **More robust version:** use the phone's multi-microphone array plus a calibrated sound-localization model; fuse compass and gyroscope data from the watch/phone.
- **Wearable expansion:** investigate stereo or multi-microphone earbuds or a small external microphone array. The watch remains the haptic/display endpoint.
- **No-bearing fallback:** show a pulsing ring or “nearby” icon rather than a misleading arrow.

The arrow should be divided into coarse sectors such as front-left, left, rear-left, rear, rear-right, right, front-right, and front. A watch face can show 8 sectors more reliably than a falsely precise degree value. The user should be able to calibrate “front” and reverse the layout for left/right wrist wear.

## Additional solutions worth embedding

Beyond captions and environmental sound alerts, the product could add:

- **Name and keyword alert:** detect the user's name or chosen words, with privacy-preserving on-device keyword spotting where possible.
- **Conversation mode:** speaker turns, “please repeat,” large text reply cards, saved phrases, and optional text-to-speech from the phone.
- **Alarm and timer awareness:** detect phone/watch alarms, calendar reminders, timers, smoke/CO alarms, and system notifications and mirror them as haptic patterns.
- **Door and room awareness:** doorbell, knocking, intercom, baby cry, appliance beeps, and a user-trained “custom sound” class.
- **Safety check-in:** long-press SOS, a trusted-contact notification, last-known location, battery/connectivity status, and a clear “this is not emergency detection” disclaimer.
- **Camera-assisted hazards:** phone camera object detection for vehicles, bicycles, stairs, crosswalk signals, and obstacles. Keep this separate from the acoustic system and never imply collision avoidance unless validated to a much higher safety standard.
- **Personal learning:** let a person correct “horn” to “truck horn,” mute an annoying source, or mark an alert useful/not useful. Feed corrections into local thresholds and the toolkit's consent-gated profile, not silently into a cloud model.
- **Accessibility controls:** left/right wrist setting, high contrast, large type, reduced motion, flash plus vibration, language selection, caption history, quiet zones, sensitivity, and a “do not alert while phone is in use” option.

Existing products and research are relevant prior art, especially the University of Washington's SoundWatch smartwatch sound-awareness work and Google's Live Transcribe/Sound Notifications. Treat them as references for user flows and evaluation, not as proof that the Watch4 alone can solve localization. [9] [10]

## Suggested MVP

**MVP 1: phone sensing, watch feedback.** Android phone foreground service captures audio; on-device speech recognition produces short caption snippets; MediaPipe/TFLite detects a small allowlist of hazards; the watch receives captions and events through the Data Layer; vibration patterns and a large event card provide the primary alert.

**MVP 2: direction and personalization.** Add phone-array direction estimation, watch heading fusion, 8-sector arrow, calibration, per-sound sensitivity, confidence/cooldown logic, toolkit profile sharing, and offline-first settings.

**MVP 3: optional camera and conversation tools.** Add camera hazard context, speaker turns, saved transcript, “repeat/type a reply,” and trusted-contact safety features.

Before public deployment, test battery drain, recognition latency, missed-alert rate, false-alert rate, caption error rate, direction error by sector, skin/wrist orientation differences, crowded streets, reverberant rooms, wind, rain, and phone/watch disconnection. Run these tests with Deaf and hard-of-hearing users from the beginning. The most important safety rule is to communicate uncertainty and maintain a visible status when sensing, connectivity, or model confidence is degraded.

## Simplest version: no custom ML training

You do **not** need to train an ML model to build the first useful prototype. Training is only needed when an existing model cannot recognize a sound reliably enough for your users, or when you want a custom sound that is not in the model's label list.

Build the easiest version in this order:

1. **Use the phone for captions.** Start a user-controlled foreground service and use Android's on-device `SpeechRecognizer` when `isOnDeviceRecognitionAvailable()` returns true. Send only the newest one or two caption lines to the watch. If on-device recognition is unavailable, show a clear “network captions” status rather than silently sending private audio to a server. [7]
2. **Use a pretrained sound model.** Add MediaPipe Audio Classifier and the recommended YAMNet model. YAMNet already predicts 521 broad audio-event categories from AudioSet, and MediaPipe supports live audio streams, score thresholds, and allowlists. Start by allowing only a small set such as `Siren`, `Vehicle horn`, `Smoke detector`, `Doorbell`, `Alarm`, and `Dog`. [11] [12]
3. **Use simple rules instead of another AI model.** Trigger an alert only when a sound remains above a confidence threshold for several consecutive windows. Add a cooldown so one passing horn does not vibrate repeatedly. Let the user turn sound types on and off.
4. **Send events to the watch.** Use the Wearable Data Layer to send `{type, confidence, timestamp}`. The watch shows a label, performs a predefined vibration, and optionally shows “direction unavailable.” Do not implement the arrow in version 1.
5. **Add direction last.** First display “nearby sound.” Then test phone-based direction estimation. Do not train a direction model until you have verified that the phone's microphone layout and compass produce useful results in real streets.

This first version can therefore be built with Android Studio, Kotlin, the Wear OS template, MediaPipe Tasks Audio, the downloadable YAMNet TFLite model, Android's speech APIs, and the Wearable Data Layer. It needs no paid AI API, cloud server, or custom dataset.

### When custom training becomes necessary

There are three common reasons to train or fine-tune:

- **The model confuses important sounds.** For example, it labels a bus air-brake as a horn or misses a quiet bicycle bell.
- **You need a local or personal sound.** Examples include a particular smoke alarm, a local emergency siren, a school bell, or a user's name being called in a noisy room.
- **You need reliable direction.** Direction-of-arrival is not just sound classification. It normally needs calibrated microphone geometry and data recorded on the actual phone, case, mounting position, and environment. Treat this as a later research feature, not an MVP task.

The least difficult custom-training approach is **transfer learning**, not training a neural network from zero. Record short labelled examples for only three to six classes, extract embeddings from YAMNet, train a small classifier on top, evaluate it on recordings from different people and places, then export the small model for phone inference. Keep a separate test set recorded on another day; otherwise the accuracy will look better than real-world performance.

### Free resources

- **Google AI Edge MediaPipe Audio Classifier:** Android code examples, live-stream support, category allowlists, score thresholds, and the recommended YAMNet model. [11]
- **TensorFlow YAMNet:** a pretrained model, label map, Colab notebook, and a simple inference example. It expects mono 16 kHz audio and predicts 521 AudioSet event classes. [12]
- **Google Colab:** hosted notebooks that can be used without local ML setup and provide free access to computing resources, including GPUs and TPUs, subject to availability and usage limits. [13]
- **Kaggle Notebooks:** a second free hosted notebook option with free GPU notebook environments documented by Kaggle. [14]
- **ESC-50:** 2,000 labelled environmental-sound recordings useful for learning and benchmarking, but check its license and class coverage before using it in a product. [15]
- **AudioSet:** the source domain for YAMNet's labels and training data. Use it for research and evaluation, and check the licensing and access terms for individual clips before redistribution or commercial use. [16]
- **Edge Impulse Developer plan:** an optional visual workflow for collecting audio, training small classifiers, and exporting edge models. Its current pricing page lists a $0 Developer plan, but confirm quotas and export support before depending on it. [17]

For this project, start with MediaPipe/YAMNet and Colab only as a learning and evaluation tool. Do not collect thousands of files or pay for GPUs until the pretrained MVP has a measured failure that custom data can fix.

## References

[1]: https://github.com/AI-for-Accessibility-Collective/AI-for-Accessibility-Toolkit "AI for Accessibility Toolkit repository and README"
[2]: https://www.samsung.com/us/support/answer/ANS10003348/ "Samsung support: Set up and use your Wear OS Galaxy smart watch"
[3]: https://news.samsung.com/ua/%D1%96%D0%BD%D1%84%D0%BE%D0%B3%D1%80%D0%B0%D1%84%D1%96%D1%BA%D0%B0-galaxy-watch4-%D1%82%D0%B0-galaxy-watch4-classic-%D0%BD%D0%BE%D0%B2%D0%B8%D0%B9-%D1%80%D1%96%D0%B2%D0%B5%D0%BD%D1%8C "Samsung Galaxy Watch4 and Watch4 Classic specification infographic"
[4]: https://developer.android.com/training/wearables "Android Developers: Build apps for Wear OS"
[5]: https://developer.android.com/training/wearables/data/overview "Android Developers: Wearable Data Layer overview"
[6]: https://developer.android.com/develop/ui/views/haptics/haptics-apis "Android Developers: Haptics APIs"
[7]: https://developer.android.com/reference/android/speech/SpeechRecognizer "Android API reference: SpeechRecognizer"
[8]: https://developers.google.com/edge/mediapipe/solutions/audio/audio_classifier/android "Google AI Edge: Audio classification guide for Android"
[9]: https://www.washington.edu/news/2020/10/28/soundwatch-alerts-ddeaf-and-hard-of-hearing-users-to-desired-sounds/ "University of Washington: SoundWatch sound-awareness smartwatch app"
[10]: https://support.google.com/accessibility/android/answer/9158064?hl=en "Google Accessibility Help: Use Live Transcribe"
[11]: https://developers.google.com/edge/mediapipe/solutions/audio/audio_classifier "Google AI Edge: Audio Classifier overview"
[12]: https://www.tensorflow.org/hub/tutorials/yamnet "TensorFlow Hub: Sound classification with YAMNet"
[13]: https://research.google.com/colaboratory/faq.html "Google Colab FAQ"
[14]: https://www.kaggle.com/docs/notebooks "Kaggle Notebooks documentation"
[15]: https://github.com/karolpiczak/ESC-50 "ESC-50 environmental sound classification dataset"
[16]: https://research.google.com/audioset/ "Google AudioSet"
[17]: https://www.edgeimpulse.com/pricing "Edge Impulse pricing and Developer plan"
