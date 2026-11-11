# Sumi Vid

A native Android video player by **Lord_Sumi** ([sumiEmpire001](https://github.com/sumiEmpire001)), built with Java and AndroidX Media3.

Sumi Vid plays local videos and direct HTTPS streams, with a searchable library, playlists, subtitles, gesture controls and persistent settings.

| Project | Details |
| --- | --- |
| Current app version | 2.3 |
| Minimum Android version | Android 8.0 / API 26 |
| Language | Java 17 |
| Playback | AndroidX Media3 ExoPlayer 1.5.1 |
| Build | Gradle 8.10.2, Android Gradle Plugin 8.7.3 |
| Licence | MIT for project code; dependency licences apply separately |

## Features

- Local video library with thumbnails, search, sorting, folders and continue watching.
- Favourites, recent watches and playlist creation, renaming and removal.
- Seek controls, 0.25×–3× speed, repeat, shuffle, sleep timer and fit modes.
- Embedded audio/subtitle selection and external SRT, VTT, SSA and ASS files.
- Double-tap and swipe gestures, touch lock and supported Android picture-in-picture controls.
- Searchable settings for playback, subtitle appearance, languages, gestures, folder filters and privacy.
- OLED black mode and four accent colours.
- No account, ads, analytics or video-upload endpoint.

## Build and test

Install JDK 17 and Android SDK platform 35 with Build Tools 35.0.0. Set `ANDROID_HOME` or your own `sdk.dir` in an untracked `local.properties` file, then run:

```bash
chmod +x gradlew
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

The development APK is written to `app/build/outputs/apk/debug/app-debug.apk`. It uses the builder's debug certificate and cannot be assumed to update an existing personal installation signed by the maintainer. Use the matching private key for compatible release updates; see [BUILDING.md](BUILDING.md).

GitHub Actions is configured to build the development APK, run tests and upload reports on pushes and pull requests. No signing secrets are needed by this workflow.

## Verification and limitations

The v2.2 personal release build passed **60 automated checks**, including Android-framework configurations for API 26, 28 and 35, provider/scan regressions and playback/queue/settings rules. See [TESTING.md](TESTING.md) for scope and [BUG_FIXES.md](BUG_FIXES.md) for the five reproduced defects and their corrections.

Framework simulation and real-engine construction tests do not certify hardware decoding. Actual video frames, audio output, Bluetooth behaviour, OEM permissions, battery use and physical-phone performance still need device testing. File formats depend on the phone's codecs. Casting, a private vault, a bundled software codec pack and a background-audio service are not included.

## Documentation

- [User guide](USER_GUIDE.md)
- [Build and signing details](BUILDING.md)
- [Release notes](RELEASE_NOTES.md)
- [Changes](CHANGELOG.md)
- [Bug fixes](BUG_FIXES.md)
- [Verification](TESTING.md)
- [Contributing](CONTRIBUTING.md)

Private signing keys, signing backups, local SDK paths and generated build directories are excluded from this project. Keep private signing material outside the repository.
