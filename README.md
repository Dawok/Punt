# Punt

An unofficial Android app for watching Kick streams and chatting with Kick and 7TV emotes. Requires Android 9 or newer.

## Features

- Browse live channels and categories, or open a channel by name or link.
- Live chat with Kick and 7TV emotes.
- Background audio, lock-screen controls, fullscreen, and picture-in-picture.
- Automatic playback recovery and a **LIVE** button to catch up with the stream.
- Pinned and recent channels, search suggestions, and separate Wi-Fi/mobile video quality preferences.

## Install

Download `punt.apk` from the latest [release](../../releases/latest), open it on your phone, and allow installation from that source when prompted. Alternatively, use ADB:

```sh
adb install -r punt.apk
```

## Usage

Browse channels or enter a channel name or Kick link in the search field and tap the arrow. Sign in to send chat messages, follow channels, and use account emotes; watching and pinning channels work without an account.

Tap **LIVE** to return to the current stream after pausing. Use Settings to adjust chat text size, animated emotes, and video quality for Wi-Fi and mobile data.

## Build and run

Open the project in Android Studio with JDK 17 and Android SDK 36, then run the `app` configuration on a phone or emulator. To build an optimized APK from a terminal, set `JAVA_HOME` to your JDK and `ANDROID_HOME` to your Android SDK, then run:

```sh
./gradlew :app:assemblePreview
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

Preview builds use your local debug signing key.

## Checks

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```
