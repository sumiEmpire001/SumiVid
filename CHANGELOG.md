# Sumi Vid 2.3

- Remove the full-frame dark overlay from playback controls so the video keeps its original brightness when controls appear.

# Sumi Vid 2.2 — final personal installation candidate

- Package a signed release variant with debugging disabled.
- Retain the signing identity and application identifier used by earlier builds.
- Derive the in-app version label from generated build metadata.
- Disable the app's development playback log in the release variant.
- Require explicit private signing configuration for release packaging.
- Exclude keystores and local signing files from source control.
- Validate the release variant with the full regression suite and Android lint.
- Physical-phone playback and production certification remain outstanding; no store publication was performed.

# Sumi Vid 2.1 — 31 October 2026

- Fix paused-state restoration and playback snapshots saved after release.
- Preserve Settings and library searches across activity recreation.
- Stop finished videos from restarting solely because the screen is restored.
- Dismiss playback dialogs on stop so their callbacks cannot target a released player.
- Cancel outdated library queries and apply the newest filter snapshot.
- Separate folders on secondary storage without changing saved media identities.
- Retain favourites, playlists and resume positions on upgrade.
- Add Android-framework regression coverage on API 26, 28 and 35, plus real ExoPlayer startup checks with a generated local test video.
- See BUG_FIXES.md and TESTING.md for evidence and the limits of these checks.

# Sumi Vid 2.0 — 31 October 2026

- Add a searchable, sectioned Android Settings screen accessible from the library gear and player More menu.
- Wire autoplay, automatic next, shuffle, resume, speed memory, screen fit, seek interval, control timeout and orientation to playback.
- Configure immersive bars, keep-screen-on and audio-route disconnect behaviour.
- Allow each gesture and floating video to be enabled independently.
- Add subtitle size, colour, background, outline, position and preferred subtitle/audio languages; provide external-subtitle removal.
- Add duration filtering, hidden-file handling, path-based folder grouping and folder exclusions.
- Add optional thumbnails, remembered sort, OLED black and four accent choices.
- Add optional watch history, history clearing and a settings reset that preserves favourites, playlists and history.
- Keep local app data compatible with previous versions and use the same signing certificate.
- Add seven settings-rules tests, bringing the unit suite to seventeen tests.

# Sumi Vid 1.1 — 31 October 2026

## Browsing

- Compact header, file-open action, highlighted navigation tabs and row menus.
- Asynchronous video thumbnails with bounded workers and an 8 MiB cache.
- Recycled ListView rows, avoiding creation of one View per video.
- Continue-watching card and per-video progress bars.
- Search by video name or folder; remembered sort preference including file size.
- Rename playlists, remove entries, and validate duplicate playlist names.

## Playback reliability

- Use a versioned app-private queue file to avoid Android's Intent parcel-size limit.
- Validate queue sizes, selection indices and incoming video schemes.
- Fix returning from the subtitle picker when the player has been released.
- Remember subtitle document access where providers support persistent grants.
- Apply the tested resume rule to playback, including short clips and finished videos.
- Remember playback speed and screen fit.
- Seek once when a horizontal swipe ends; lock the gesture direction during each swipe.
- Sleep timer tracks a deadline and prevents auto-resume after expiring while stopped.
- Add floating-window play/pause and seek controls with a private receiver on supported Android versions.
- Reduce resume checkpoint writes from every second to every five seconds; save immediately on stop.
- Clear the keep-screen-on flag when playback pauses.

## Validation

Unit tests cover seek/resume timestamps, queue round trips with Unicode and 10,000 entries, and malformed queue rejection. See TESTING.md for exact results. Runtime media features still require physical-device verification; this release does not claim MX Player feature parity.
