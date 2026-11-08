# Setup and building

This guide takes you from a fresh clone to a phone and a watch running Open Caption.

## What you need

| Item | Version used by the project | Notes |
|------|-----------------------------|-------|
| Android Studio | Iguana (2023.2) or newer | Or just the command-line tools |
| JDK | 17 | Android Studio's bundled JDK works |
| Android SDK | Platform 35, build-tools for 35 | Installed from SDK Manager |
| Gradle | 8.x (via the included wrapper) | Do not install Gradle separately |
| Phone | Android 8.0+ (minSdk 26), 64-bit ARM | Tested on Samsung Galaxy S20 Ultra |
| Watch | Wear OS 3+ (minSdk 30) | Tested on Samsung Galaxy Watch4 |
| Platform tools | Recent `adb` | `brew install android-platform-tools` on macOS |

Phone and watch must be paired in the **Galaxy Wearable** app (or the Wear OS companion app on other phones).

## 1. Clone and configure

```sh
git clone https://github.com/keya254/deafy.git
cd deafy
```

Create `local.properties` in the project root with the path to your Android SDK. This file is machine-specific and is never committed:

```properties
sdk.dir=/Users/you/Library/Android/sdk
```

Android Studio writes this file for you when you open the project.

## 2. Build

```sh
./gradlew :shared:testDebugUnitTest   # protocol and label unit tests
./gradlew :phone:assembleDebug        # phone APK (also bundles the watch APK)
./gradlew :wear:assembleDebug         # watch APK on its own
```

Outputs:

- `phone/build/outputs/apk/debug/phone-debug.apk`
- `wear/build/outputs/apk/debug/wear-debug.apk`

The phone APK includes the watch APK (`wearApp(project(":wear"))` in `phone/build.gradle.kts`). When the phone app is installed, Galaxy Wearable normally installs the watch app on the paired watch automatically. If that does not happen, use the manual steps below.

## 3. Install the phone app

Enable **Developer options** and **USB debugging** on the phone, connect it, then:

```sh
adb install -r phone/build/outputs/apk/debug/phone-debug.apk
```

Open **Open Caption** once. Grant the microphone and notification permissions when asked.

## 4. Install the offline speech models

Captions are fully offline when the models are installed. See [MODELS.md](MODELS.md) for where to download them and what each file is.

```sh
scripts/install-models.sh path/to/models
```

The script copies the files into the app's storage folder on the phone. Open Caption must be installed first. The Settings screen shows whether the offline model was found.

## Install the watch app

Enable wireless debugging on the watch. In Wear OS, open **Settings → About watch → Software info** and tap **Software version** several times to unlock **Developer options**. Then open **Developer options** and turn on **Debug over Wi-Fi** (the menu name varies by Wear OS version). Keep the watch on the same Wi-Fi network as your computer.

Find the watch's address and connect:

```sh
adb mdns services                       # lists devices advertising _adb-tls-connect._tcp
adb connect 192.168.1.50:37123          # use the address shown for the watch
adb devices -l                          # the watch should show as "device"
adb -s 192.168.1.50:37123 install -r wear/build/outputs/apk/debug/wear-debug.apk
```

Wireless debugging ports change when the watch restarts. Run `adb mdns services` again if the connection drops.

## Upgrading from an older build

Earlier builds used the package name `com.deafy.app`. The current package is `tech.briankeya.opencaption`, so the two can be installed side by side. Remove the old one to avoid confusion:

```sh
adb uninstall com.deafy.app                                     # phone
adb -s <watch-serial> uninstall com.deafy.app                   # watch
```

Settings and caption history are stored per package, so they do not carry over.

## Running the apps during development

- Run the phone app from Android Studio with the **phone** configuration.
- Logs: `adb logcat -s SoundEngine MicStream LocalSpeech ListenService`
- Check that the app and the watch see each other: the phone's home screen shows the connected watch name.

## Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
