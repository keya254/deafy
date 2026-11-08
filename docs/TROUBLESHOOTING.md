# Troubleshooting

Start with the status line on the phone's Home screen. It explains most problems in plain words.

## Captions

**"Offline model not installed (N files missing)"**
Copy the speech models to the phone with `scripts/install-models.sh`. The file names must match exactly. See [MODELS.md](MODELS.md). Until then, captions use Android's built-in recognizer, which may need internet for some languages.

**"Microphone permission is needed for captions."**
Open Android **Settings → Apps → Open Caption → Permissions** and allow **Microphone**, then turn captions on again.

**Captions are far behind the speaker**
- Check the decode time in the log: `adb logcat -s LocalSpeech`. Lines look like `decoded 3.8 s of speech in 1900 ms`. If decoding takes longer than the speech, the phone cannot keep up. Switch to a smaller Whisper model.
- Close other heavy apps and keep the phone cool. Phones throttle when hot.
- Turn off **Clean background noise** in Settings if you need the last bit of speed.

**Captions appear but are wrong or in the wrong language**
- Set the language in Settings to the speaker's main language, rather than Mixed, if the speaker uses one language most of the time.
- Move the phone closer to the speaker. Raise **Microphone boost** in Settings carefully. High values also amplify background noise.
- Accuracy for Kenyan accents and Swahili has not been formally measured. See [ROADMAP.md](ROADMAP.md).

**Nothing appears at all**
Speak for a few seconds, then check that the phone's microphone is not covered. Try the boost setting. Check `adb logcat -s LocalSpeech` for errors.

## Sound alerts

**"Sound alerts unavailable: …"**
The message after the colon is the cause. Copy it into a bug report, along with `adb logcat -s SoundEngine MicStream`.

**The sound alert switch turns itself off**
The sound engine failed to start. Read the status line. This is the most common symptom of a failed start.

**A sound is not alerting**
- Check that the sound is enabled in Settings, under **Sound alerts**.
- Lower **Sensitivity** in Settings. Real-world sounds often score lower than clean recordings. The default is 30%.
- An alert needs the sound to be detected in two half-second windows in a row. Short or quiet sounds can be missed.
- Each sound has a 10-second cooldown, so a second horn straight after the first does not alert again.

**Too many alerts**
Raise **Sensitivity**, or turn off sounds you do not need. "Traffic", "Car", "Truck", and "Bus" are off by default because they run constantly on busy streets.

## Watch

**Watch says "Phone not nearby"**
- Keep the phone on, within Bluetooth range, and with the Open Caption app open at least once since the phone started.
- Check the phone shows the watch's name in its top bar. If not, pair the devices in Galaxy Wearable.

**Watch toggle flips back**
The phone did not start that feature, so it reports the old state. Check the phone's status line.

**Watch shows no app or the old app**
- Install the watch app manually: see [SETUP.md](SETUP.md#install-the-watch-app).
- Remove the older `com.deafy.app` build if it is still there.

## Installing and adb

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**
The installed app was signed with a different key (for example, a release build over a debug build). Uninstall the old app first. This deletes its settings and history.

**`adb devices` does not show the watch**
- Turn on **Debug over Wi-Fi** on the watch, and keep both devices on the same network.
- Run `adb mdns services` to find the current address. Wireless debugging ports change when the watch restarts.
- Some Wi-Fi networks block device-to-device traffic (client isolation). Try a different network.

**The models script says the app is not installed**
Install and open the phone app first. The models folder is created by the app.

## Reporting a problem

Open an issue on GitHub with:
- The phone and watch models and Android and Wear OS versions
- What you did, what you expected, and what happened
- The status line text
- The relevant log output, from `adb logcat` with the tags above

Do not include audio recordings or caption history unless you have checked them first. They may contain private conversations.
