# Sumi Vid 2.2

A native Android video player built for Lord_Sumi. Android 8.0 or newer.

## Included

- Searchable Settings screen with persistent playback, gesture, subtitle, audio, library, appearance and privacy controls
- OLED mode, four accent colours, configurable seek interval, shuffle, subtitle styling and language preferences

- On-device MediaStore video library, folders, text search, newest/name/duration/size sorting
- Video thumbnails, recycled scrolling rows, continue-watching card and resume progress bars
- Favourites, recent history, playlist creation/renaming/deletion, removal of playlist entries, video details and Android sharing
- Android file picker and Open With support; app-wide storage permission is optional
- Media3 ExoPlayer playback, seek bar, 10-second seeks, next/previous, automatic next preference
- Embedded audio/subtitle track selection and external SRT/VTT/SSA/ASS subtitle files
- 0.25x–3x playback speeds, fit/zoom/stretch, manual rotation
- Double-tap seek, horizontal swipe seek, brightness/volume swipes, touch lock
- Playback position persistence, resume preference, repeat one/all, sleep timer
- Picture-in-picture with play/pause and seek actions on devices that support and permit it
- Direct HTTPS file, HLS and DASH stream playback
- No ads, analytics, login or video uploads

## Install

Open SumiVid-v2.2.apk on an Android phone and allow installation from your browser or file manager when Android prompts. Open Sumi Vid, tap Allow video access to scan the library, or use the ＋ button to choose an individual file. The ⋮ menu contains network streaming and access management. Each row has an options button; holding a row opens the same menu.

The app is not an iPhone app. Codec playback depends on the phone's decoders. It has no bundled FFmpeg codec pack, casting, DRM service login, online content library, equalizer, private vault or background-audio service. Floating playback uses Android picture-in-picture. The library recycles list rows and loads thumbnails on a bounded background pool with an 8 MiB memory cache. Playlist queues use app-private files rather than large Intent parcels. External subtitle choices are remembered when their document provider permits persistent access.

## Changes in 2.2

See BUG_FIXES.md for reproduced bugs and corrections, USER_GUIDE.md for settings and controls, CHANGELOG.md for this release and TESTING.md for validation limits. This is the finalized personal installation candidate; physical-device validation remains required before production distribution.

## Build

JDK 17, Android SDK platform 35, Android build tools 35.0.0 and Gradle 8.10.2.

Set `sdk.dir` in your own `local.properties`, then:

```
./gradlew assembleDebug testDebugUnitTest lintDebug
```

The playback engine is AndroidX Media3 1.5.1, with HLS, DASH and UI modules. See https://developer.android.com/media/media3/exoplayer/hello-world . Third-party dependencies are governed by their own licences, principally Apache 2.0 for Media3. Gradle downloads them when building.

## Privacy

Local video metadata and playback preferences stay on the device. The Internet permission is used only to access a stream or resource you explicitly open. A chosen stream server receives the normal network request. The app does not include analytics, advertising, or a video upload endpoint. Application backup is disabled.

## Signing continuity

The 2.2 APK uses the release build variant with debugging disabled. It retains the personal signing certificate from earlier beta builds for in-place update compatibility. That certificate originated as the project's debug signing key; the build variant change does not change the certificate's origin. Store submission and production certification have not been performed.

The separate private build backup preserves the signing key. Keep it out of public repositories. A newly generated key will not update the installed app.

Signed packaging requires these environment variables: `SUMI_SIGNING_STORE` (absolute keystore path), `SUMI_SIGNING_STORE_PASSWORD`, `SUMI_SIGNING_KEY_ALIAS`, and `SUMI_SIGNING_KEY_PASSWORD`. Obtain the values from your private build backup, then run:

```
./gradlew assembleRelease testReleaseUnitTest lintRelease
```

The build refuses to package a release when signing details are missing. Unit tests and lint can run independently of signing. No private signing file is included in this source archive.
