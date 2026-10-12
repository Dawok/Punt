# Punt

A native Android viewer for Kick streams, with live chat and inline Kick/7TV emotes. Built with Kotlin, Jetpack Compose, Media3 ExoPlayer, OkHttp, kotlinx.serialization, coroutines, and Coil 3.

The Android and Gradle project names are Punt. The application ID remains `app.kickpocket` so existing installations, account sessions, and local settings carry forward when updating.

## Features

- Browse live channels and categories, sort by viewers or Kick recommendations, open a category to filter live streams, and load more results. Recommendations require login.
- Open a channel by name or by pasting its Kick link.
- Open Kick’s sign-in form directly in an in-app WebView, with visible verification errors and a reload action. Signed-in startup opens Following; view live followed channels and send chat messages.
- Follow or unfollow a live or offline channel from its header. The button reads your account’s current status, shows progress, and refreshes Following after a successful change. Failed changes offer a status reload before another attempt.
- Native emote names typed in chat are converted to Kick markup; 7TV aliases remain plain text.
- Open the emote picker beside the message box to search channel Kick/7TV emotes and filter by provider. Selecting an emote inserts its name at the cursor or replaces selected text; press Send to send the draft. The picker respects the 500-character limit and animated-emote preference.
- HLS playback with adaptive or manual video quality, standard playback controls, and fullscreen.
- Portrait video above chat; landscape video beside chat.
- Readable stream titles and compact quality/fullscreen/live/viewer overlays inside the player, hidden with its controls.
- Automatic picture-in-picture when switching apps during playback, with a play/pause action and a video-only layout.
- Public live chat with reconnect/backoff, heartbeat handling, username colors, moderator/broadcaster indicators, and moderation events.
- Native Kick emote markup plus global and channel 7TV sets, animated WebP, channel aliases, and zero-width overlays.
- Adjustable chat text size and a setting to show static emotes.
- Up to 12 locally saved recent channels, with a clear-history action.
- A clearly labeled sample stream with simulated chat for previewing playback and emotes.

Android 9 (API 28) or newer is required. This lets animated WebP use Android's native ImageDecoder. Viewing public streams and browsing do not require an account or backend. Login is optional and is required for sending chat, account follows, and personalized recommendations.

## Build and run

Open this directory in Android Studio. Install JDK 17, Android SDK Platform 36, and the build tools requested by Gradle. Let Android Studio create an untracked `local.properties` pointing to your SDK, then select an emulator or connected phone and run `app`.

From a terminal with `JAVA_HOME` and `ANDROID_HOME` configured:

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For an optimized installable build, run `./gradlew :app:assemblePreview` and install `app/build/outputs/apk/preview/app-preview.apk`. This build uses release optimization with the local debug signing key for development. Configure your own release signing before publishing.

Android instrumentation tests use the matching debug app and debug Android test APKs. The optimized preview APK cannot be paired with the debug test APK because optimization changes the dependency classes used by the test runner.

The repository includes the official Gradle wrapper with its distribution checksum. Versions are pinned to a compatible set: AGP 8.13.2, Gradle 8.13, Kotlin 2.2.21, Compose BOM 2025.10.01, and Media3 1.8.0. These are deliberate pins rather than a claim to use every library's latest release.

## Performance design

The ExoPlayer instance belongs to the ViewModel and survives rotation. A single attached player view is retained across portrait, landscape, fullscreen, and PiP layouts so mode switches do not replace the video surface. Player and chat observe separate state flows. HTTP JSON parsing and emote catalog construction run away from the main thread. Chat callbacks also parse and apply messages away from the main thread; bounded snapshots publish at most ten times per second. The 500-message buffer deduplicates incoming messages and applies deletion, ban, and clear events before publication.

The chat list uses stable message keys and lazy rendering, with no per-message entrance animations. Scrolling away from the newest messages pauses automatic following; “Jump to live” resumes it. The picker also uses a lazy grid and the same cached images as chat, loading only visible cells. Emote requests use 7TV's 2x WebP assets and a shared image loader with bounded memory and disk caches. Static mode requests 7TV's static variants and decodes only the first frame of native animated emotes. Each visible animated image still has decoding/rendering costs; there is no claim that animation frames are shared across occurrences.

Playback and chat continue while the activity is visible in picture-in-picture. They pause when the activity stops, including when PiP is dismissed. Automatic PiP uses Android 12+ auto-entry, with `onUserLeaveHint` on Android 9–11. It is disabled for paused/offline streams and while account/settings dialogs are open. Device PiP support and the app’s system PiP permission are required. The screen stays awake while video is playing or buffering. A six-second live offset is a starting preference; the source playlist and network determine achievable latency. Profile busy channels on physical phones before tightening buffering or introducing a custom chat renderer.

## Integration boundaries

Browsing, categories, channel metadata, account follows, chat sending, and playback URLs currently come from Kick's **undocumented website endpoints**. Public chat currently uses the website's Pusher connection. These interfaces can change, return a Cloudflare/403 response, or be unavailable on some networks. The app reports failures and does not try to solve or bypass challenges. Errors loading optional emote catalogs leave text chat usable.

7TV sets are fetched from its v3 global and Kick-user endpoints. A channel without a linked 7TV account still receives global emotes. Catalogs refresh periodically while watching. Per-user 7TV cosmetics and personal sets are not included yet.

The official [Kick developer API](https://github.com/KickEngineering/KickDevDocs) documents OAuth and webhook events; it is separate from the direct viewer interfaces above. The current official OAuth token exchange requires a client secret, so this app uses the website session without embedding an OAuth secret. Keep any future OAuth client secret on a backend, never in the APK.

Login uses the real Kick website, including its verification/2FA prompts. The browser honors the site's viewport metadata. A small page helper opens Kick's own login button when needed and expands a clipped sign-in overlay; it checks field geometry without reading field values, cookies, or tokens. The app does not provide a native password form or a JavaScript bridge. Session cookies remain in Android’s app-private WebView cookie store; account tokens are never written to preference files or logs. A non-sensitive boolean records whether there is a session to restore, so anonymous browsing never initializes WebView. Authenticated requests are restricted to HTTPS `kick.com` and read-only requests to two specific recommendation endpoints on `web.kick.com`, use a dedicated client that refuses redirects, and never share credentials with emote, image, or video requests. Sign out clears the app’s WebView cookies and site storage. Website challenges or a very old Android WebView can prevent login; the app does not bypass them. Channel-specific chat rules and account restrictions still apply. Failed sends retain the draft and are not retried automatically.

Upcoming work: background audio without PiP and additional emote providers such as BTTV/FFZ. This version is an unofficial client and is not affiliated with Kick or 7TV.

## Source layout

- `data/`: channel input, discovery parsing, public HTTP requests, account sessions, emote catalogs, and local preferences.
- `chat/`: Pusher transport, bounded chat reducer, and inline emote tokenization.
- `player/`: playback lifecycle and video track selection.
- `ui/`: Compose home screen, player/chat layouts, settings, and theme.
- `StreamViewModel.kt`: channel session, chat sending, foreground lifecycle, and periodic refresh.
- `BrowseViewModel.kt`: discovery tabs, category filtering, pagination, and account state.
- `MainActivity.kt`: automatic PiP, player bounds, and PiP actions.

Unit tests cover input validation, live/offline response parsing, ID separation, Pusher payloads, emote variants/overlays, moderation, bounded history, HTTP cancellation, and WebSocket subscription/ping handling. Additional JVM checks cover discovery pagination, live-only follows, authenticated request origin restrictions, native-emote chat payloads, follow status and mutation methods, duplicate taps, failures, and stale channel/account responses. Android tests cover navigation, input errors, settings, fullscreen, sample video decoding, real browsing/category filtering, sorting controls, follow controls, sign-in feedback and renderer recovery, and PiP entry/playback/return when the device supports PiP.

The optional `LiveChannelSmokeTest` uses a channel supplied at run time. After installing the app and Android test APK, run:

```sh
adb shell am instrument -w \
  -e class app.kickpocket.LiveChannelSmokeTest \
  -e kickChannel CHANNEL \
  app.kickpocket.test/androidx.test.runner.AndroidJUnitRunner
```

The login layout regression uses a controlled page with a missing and clipped form. To additionally check the real Kick username/password fields in Android's WebView, opt in on a device with a current WebView:

```sh
adb shell am instrument -w \
  -e class app.kickpocket.LoginFormSmokeTest \
  -e verifyKickSignIn true \
  app.kickpocket.test/androidx.test.runner.AndroidJUnitRunner
```

This checks that both fields are on screen and reachable. It does not enter credentials or verify successful authentication.

Reference interfaces:

- [Android picture-in-picture documentation](https://developer.android.com/develop/ui/views/picture-in-picture)
- [Kick website endpoint catalog](https://github.com/fb-sean/kick-website-endpoints)
- [Media3 HLS documentation](https://developer.android.com/media/media3/exoplayer/hls)
- [Coil animated image support](https://coil-kt.github.io/coil/gifs/)
- [7TV extension integration](https://github.com/SevenTV/Extension/tree/master/src/site/kick.com)
- [Media3 sample stream list](https://github.com/androidx/media/blob/release/demos/main/src/main/assets/media.exolist.json), which includes the Apple HLS sample used here.

See [verification results](docs/validation.md) for test results and remaining device checks.
