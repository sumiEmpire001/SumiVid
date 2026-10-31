# Contributing

Use JDK 17, Android SDK 35 and Build Tools 35.0.0. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before opening a pull request.

For a bug fix, describe the trigger, the old behaviour and the corrected behaviour. Add a focused regression test for a meaningful behaviour change. Preserve existing app-data identities and playback state when changing library or lifecycle code.

Use the issue template to report phone-specific problems. Never commit signing keys, signing passwords, APK signing backups, personal videos, `local.properties` or generated build files. Development APKs have different signing identities from the maintainer's personal release and must not be presented as compatible updates.
