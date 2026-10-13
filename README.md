# Punt

An unofficial Android app for watching Kick streams, chatting, and using Kick/7TV emotes. Requires Android 9 or newer. Sign in to send messages, follow channels, and use account emotes.

## Install

Install a built APK on your phone, or use ADB:

```sh
adb install -r artifacts/punt-0.6.1.apk
```

Updates keep the same Android package ID and app data.

## Build and run

Open the project in Android Studio with JDK 17 and Android SDK 36, then run the `app` configuration on a phone or emulator. From a terminal, configure `JAVA_HOME` and `ANDROID_HOME`, then run:

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For an optimized APK:

```sh
./gradlew :app:assemblePreview
adb install -r app/build/outputs/apk/preview/app-preview.apk
```

Preview builds use the local debug signing key. Configure release signing before publishing.

## Automatic APK builds

The `Build APK` GitHub Actions workflow runs on pushes to `main`, pull requests
targeting `main`, and manual runs from the Actions tab. It uses Java 17 and Android
SDK 36, runs unit tests and lint, and builds an optimized preview APK.

To download it, open **Actions → Build APK → a successful run → Artifacts** and
download `punt-apk-<run number>`. Extract `punt.apk` from the downloaded archive
and install it on your phone. APK artifacts are retained for 30 days; check
reports are retained for 14 days.

For preview builds that update your existing installation, add the repository
Actions secret `ANDROID_DEBUG_KEYSTORE_BASE64` using the same debug keystore as
your local builds. With GitHub CLI authenticated in this repository:

```sh
base64 < "$HOME/.android/debug.keystore" | gh secret set ANDROID_DEBUG_KEYSTORE_BASE64
```

The keystore must use the standard Android debug alias and passwords. Keep a
backup of it. Without this secret, runners generate a new debug signing key,
so installing another build may require uninstalling the previous app and
losing its local data. Pull request builds always use a temporary signing key.
These are preview builds; configure permanent release signing for public
distribution. The workflow uploads build artifacts to Actions; website uploads
and GitHub Releases are separate publishing steps.

## How it works

Kotlin and Jetpack Compose provide the interface; Media3 handles video playback.
Audio continues with the screen off or while using another app, with playback
controls in the media notification and on the lock screen. Video is disabled
while the activity is stopped and restored when returning to the app or PiP.
Pausing stays paused when returning. Leaving the channel or removing the app
from recent tasks stops playback.

Tap **LIVE** in the player controls to jump back to the live position and resume
after pausing or falling behind. The indicator is green near live and gray
while paused or behind. Streams recover automatically when playback falls
outside the available live window.

Kick's website endpoints provide streams, chat, account access, and native
emotes. Chat discovers its current transport before connecting, supports
Pusher and Centrifugo, and renews expiring connection tokens. 7TV supplies
global and channel emotes. Login uses Kick's own website in an app-private
WebView.

The picker puts owned collectibles and accessible channel emotes first, then your most-used emotes. Usage is saved on this device after successful sends. Picking an emote edits the draft; press Send to send it.

## Checks

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```
