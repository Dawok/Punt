# Punt

An unofficial Android app for watching Kick streams, chatting, and using Kick/7TV emotes. Requires Android 9 or newer. Sign in to send messages, follow channels, and use account emotes.

## Install

Install a built APK on your phone, or use ADB:

```sh
adb install -r artifacts/punt-0.6.3.apk
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

## In-app updates

The app checks for updates when opened, at most once every six hours. You can
also use **Settings → Check for updates**. A home-screen banner appears when a
newer build is available. Download the update in Settings, then tap **Install
update**. Android may first ask you to allow installations from Punt, then
asks you to confirm installing the APK. The updater verifies the checksum,
application ID, version, and compatibility with the installed signing key.

The repository must be public for the app to download releases anonymously.
No GitHub access token is included in the app. CI automatically configures the
feed from `GITHUB_REPOSITORY`; each feed points to its exact release APK rather
than a changing latest download. CI uses `1000 + GITHUB_RUN_NUMBER` as the
Android version code, so new builds can be detected even between version-name
changes.

For local builds with GitHub updates enabled:

```sh
GITHUB_REPOSITORY=OWNER/REPOSITORY ./gradlew :app:assemblePreview
```

Alternatively, set `UPDATE_MANIFEST_URL` to an HTTPS update feed. Plain local
builds without either setting show that updates are not configured. Set
`ANDROID_VERSION_CODE` when building locally to replace a higher-numbered CI
installation. The package ID and signing key must still match.

## Checks

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```
