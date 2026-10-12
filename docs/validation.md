# Punt 0.5.1 verification

- Renamed the app label, home header, settings branding, launcher monogram, application/UI classes, Gradle root project, and T3 project to Punt. Public HTTP and player user agents use the new name and current build version.
- The Android application ID remains `app.kickpocket` to support updating existing installations and retaining their app data.
- Debug, Android test, and optimized preview APKs build successfully. All 38 JVM tests pass and lint reports zero errors. The preview APK signature verifies, and its packaged launcher label is `Punt` with version code 7 / version 0.5.1. The installable copy is `artifacts/punt-0.5.1.apk`, outside Gradle's output directories so later builds do not remove it.

## Earlier version 0.5.0 verification

- Debug, Android test, and optimized preview APKs build successfully. Android lint completes with zero errors. The optimized preview signature verifies; `kick-pocket-0.5.0.apk` is about 4.4 MB and installs and launches successfully on API 30.
- All 38 JVM tests pass. New insertion checks cover cursor placement, replacing forward/reversed selections, whitespace boundaries, consecutive emotes, complete tokens within the 500-character limit, and native Kick markup encoding at send time.
- The composer has a searchable Kick/7TV picker next to the draft. Selection edits the draft only; it does not call the send function. The picker uses the existing emote catalog and cached image renderer, respects the animation setting, and suppresses automatic PiP while open.
- Successful send feedback clears the draft. Failures retain it, and saved drafts are not cleared by an earlier successful-send counter when the UI is restored.
- All four picker/composer Android checks pass on API 30: selection without a send request, explicit sending, failure retention and success clearing, provider filters and search, replacing selected text, dismissing empty results, full drafts, saved-state restoration, and switching channels. The existing sample-chat navigation check also passes.
- The focused search/selection check also passes in a 1920 × 1080 landscape viewport with the software keyboard enabled. An initial emulator input/focus timeout aborted that run; its bounded retry passed. Search's Done action hides the keyboard to browse results.

Live account chat delivery and physical-device performance remain unverified. Automated composer tests use controlled emote images and a fake send callback, without posting messages to real channels.

## Earlier version 0.4.0 verification

- Debug, Android test, and optimized preview APKs build successfully. The optimized preview signature verifies; the versioned `kick-pocket-0.4.0.apk` is about 4.4 MB and installs and launches successfully on API 30.
- All 34 JVM tests pass. Follow tests cover the authenticated POST/DELETE methods, channel slug validation, existing follow status, empty mutation responses, duplicate taps, failed-request recovery, sign out, and late responses after switching channels or accounts.
- Android lint completes with zero errors.
- Both follow-control Android checks pass on API 30: sign-in routing, follow/unfollow labels and actions, disabled loading states, retry without another mutation, and visibility in portrait/landscape channel headers. The control is hidden in fullscreen.
- The follow and unfollow methods were checked against Kick's current frontend bundle. They use `/api/v2/channels/{slug}/follow`; account status uses `/api/v2/channels/{slug}/me`. Following refreshes after a successful change. A failed mutation requires a status reload because a lost response can occur after the server applies a change.
- Login on a physical phone was reported working after the version 0.3.1 form fix.

Live authenticated follow/unfollow changes, session restoration, recommendations, and chat delivery remain unverified with a real account. Automated follow tests use controlled state and do not change real accounts or send messages.

## Earlier version 0.3.1 verification

- Debug, Android test, and optimized preview APKs build successfully. The version 0.3.1 preview signature verifies; the APK is about 4.4 MB. The optimized APK installs and launches successfully on API 30. A versioned copy is available as `kick-pocket-0.3.1.apk` beside `app-preview.apk`.
- All 26 JVM tests pass, and Android lint completes with zero errors.
- The live Kick form was checked in a current browser at a 412 × 660 mobile viewport. The page helper opened Kick's real login button. A deliberately clipped overlay made the password field unreachable; the helper expanded it and restored field visibility. An overlay positioned outside the viewport was also corrected.
- The Android form-layout regression passes on the API 30 Automated Test Device (one test). It loads a controlled page directly into the production login component, simulates a first click before website handlers are ready, opens a missing form, expands an overlay clamped to 32 pixels, checks both fields are on screen and reachable, and repeats those checks after Continue feedback appears.
- Renderer failure recovery also passes on API 31. Native browser bounds were measured and remained unclipped.
- The API 35 emulator's Android system repeatedly restarted during verification. API 31's WebView 91 renderer also crashed on the controlled page. The API 30 ATD completed that check; its rendering limitations still do not establish physical-device performance.

Successful authentication, 2FA, authenticated follows/recommendations, session restoration, and chat delivery remain unverified with a real account. The real Kick form has not yet been verified inside Android's WebView on a current physical phone. The opt-in `verifyKickSignIn` Android test checks actual field visibility without entering credentials. No messages were sent to real channels.

## Earlier version 0.3 checks

- Debug, Android test, and optimized preview APKs build successfully. The preview signature verifies and the APK is about 4.4 MB.
- All 26 JVM tests pass. Additional checks cover the different viewer/recommendation endpoints and cursor parameters, recommendation order and thumbnail parsing, and restricted authenticated recommendation requests.
- Android lint completes with zero errors.
- The Android discovery check passes on API 31: viewer ordering, switching to Recommended while signed out, the login prompt, live/category/category-filtered browsing, and visible Continue feedback when no session exists.
- Kick’s `?auth=sign-in` link was verified in a current browser at a mobile viewport: it opens the email/username and password form directly. The previous app loaded the homepage instead, and Continue silently returned without a session.
- The API 31 emulator ships WebView 91. It crashed during a longer sign-in-page load. The app now handles renderer failure with a reload/update message. The focused Android recovery check passed (one test): it exercises renderer failure, confirms the recovery message and Reload sign-in control are visible, and cancels back to browsing without an app crash. Successful authentication on that outdated engine has not been verified.
- Following startup is implemented for session restoration and account verification; later tab choices remain available. The account transition still needs an end-to-end check with a real account.

No account credentials were supplied. Successful login/2FA, authenticated recommendations, session restoration, account-specific follows, and real chat delivery still need an end-to-end check with an account on a phone with an updated Android System WebView. No messages were sent to real channels.

## Earlier version 0.2 checks

- Debug APK, Android test APK, and optimized preview APK build successfully.
- All 22 JVM unit tests pass. New checks cover live cards and cursor pagination, category pagination, live-only account follows, unexpected API responses, authenticated request host restrictions, cookie/CSRF headers, and chat payloads using the native Kick emote parser.
- All six app/discovery checks pass against the final layout on a normal API 31 emulator: sample navigation/back, invalid links, fullscreen/player preservation, settings persistence/history clearing, sample HLS video decoding, and real Kick live browsing/category filtering/login-window navigation. Five passed together; the fullscreen check passed in a focused rerun with playback paused to isolate rotation/navigation from continuous software rendering. The earlier layout also passed all six checks on an API 30 Automated Test Device.
- The API 30 ATD does not expose the PiP feature, so its two PiP tests were skipped. A normal API 31 emulator is used separately for automatic PiP verification. Both PiP checks pass: automatic entry/continued playback/return with the same player view, and paused playback staying out of PiP.
- Android lint completes with zero errors. Its 19 warnings concern dependency updates and backup guidance.
- The optimized preview APK is about 4.4 MB and its debug signature verifies successfully. This signature is for development, not store publication.

The initial API 31 PiP checks exposed slow first video startup in software emulation, a test that needed to await activity resume, and playback interruption from replacing the player view during a layout change. The app now keeps the video view attached as only its bounds change; the PiP check confirms that the view is retained and the playback position advances in the floating window. The test runner accepts `-e videoStartupMs 180000` for slow emulators; the default remains 60 seconds.

The public browsing check used Kick’s live feed, category list, and category-filtered streams. A separate direct probe also verified that opaque live-feed cursors return the next page without overlapping the first page.

Account login is implemented using the real Kick website and its website session cookie. Opening and closing the login UI was checked, but no account credentials were supplied: successful login/2FA, session restoration, account-specific followed channels, and authenticated chat delivery still need an end-to-end check with an account. No chat messages were sent to real channels during automated testing.

The earlier optional channel integration check verified metadata, a public chat subscription, and emote catalogs on an offline channel. Live Kick video and incoming real chat messages have not been validated by that check. The sample playback test verifies HLS decoding independently.

ATDs [disable hardware rendering](https://developer.android.com/studio/test/managed-devices), so the API 30 checks validate interaction semantics and the playback pipeline rather than screenshots or physical-device frame rates. Smoothness under busy live chat, battery use, low-memory behavior, and device-specific PiP transitions still need testing on a physical phone. Android’s app PiP permission must be enabled for automatic PiP to appear.
