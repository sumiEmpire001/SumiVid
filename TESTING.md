# Sumi Vid 2.3 verification

- [GitHub Actions](https://github.com/sumiEmpire001/SumiVid/actions/runs/43121266478) passed Android lint, the development APK build, and all 63 automated tests on the v2.3 source commit.
- The new regression test verifies that the player controls have no full-frame dimming background on API 26, 28, and 35.
- The signed release APK built successfully with `assembleRelease`. Its signature and manifest were checked for the original signing certificate, package `com.sumi.vid`, version 2.3 (code 6), and minimum Android API 26. Debugging is disabled.
- On-device rendering, hardware decoding, and any remaining flicker still need verification on a physical phone.

## Previous verification for Sumi Vid 2.2 — 31 October 2026

## Verified

- `assembleRelease testReleaseUnitTest lintRelease`: BUILD SUCCESSFUL.
- 60 release-variant tests passed; no failures or skips.
- 39 Android-framework cases: 13 each on API 26, 28 and 35.
- 4 provider/scanning regression cases on API 35.
- 17 pure settings, playback-math and queue-serialization cases.
- Release lint reported: No issues found.
- APK signature verified; same signing certificate as v2.1 and earlier versions.
- Manifest verified: no debuggable or test-only application flag.
- com.sumi.vid, version 2.2, versionCode 5, min SDK 26, target SDK 35.

## What these tests establish

Startup, Settings navigation and saved options, search persistence, reset behaviour, lifecycle snapshots, modal dismissal, malformed intents, subtitle result delivery, permission revocation, scan cancellation/filter changes, storage grouping and preservation of existing media identity are covered. The five reproduced defects in BUG_FIXES.md remain corrected.

Controlled lifecycle tests mock the ExoPlayer interface. Separate real-engine tests create ExoPlayer with a generated local MP4 and check a paused single-item startup in all three framework configurations. The test-only Bluetooth shadow documented in BUG_FIXES.md works around a Robolectric reflection defect and does not validate Bluetooth hardware.

## Known validation boundary

This is a finalized personal installation candidate, not production certification. No physical phone was connected. Decoded video frames, real audio output, Bluetooth routing, OEM storage permissions, picture-in-picture transitions, battery use and physical-device performance still require checks on target phones. Previous software-emulator attempts are not counted as device playback passes. No app-store submission was performed.

The release variant disables debugging but retains the personal certificate originally generated for the earlier debug builds so installed users can update without uninstalling. See README.md for signing continuity and release-build setup.
