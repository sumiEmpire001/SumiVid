# Sumi Vid 2.1 — reproduced bugs and corrections

| Bug | Before the fix | Corrected behaviour | Regression coverage |
| --- | --- | --- | --- |
| A user pause was lost when saving activity state | The saved `play` flag could remain true after the player was paused | Read the current user intent when saving; distinguish a lifecycle pause from a user pause | `userPauseIsPreservedInSavedState`, `lifecyclePauseDoesNotErasePlayingIntent` |
| Playback state disappeared after stopping the player | A state save after release omitted the playback position (`-1` in the failing test instead of 12000 ms) | Capture the current item, position and desired play state before release, and save them even after release | `stoppedPlayerStateIsNotLost` |
| Settings search disappeared during screen recreation | Searching for subtitles returned to an empty query | Save and restore the settings query and scroll position; also preserve the library query | `settingsSearchSurvivesRecreation`, `librarySearchSurvivesRecreation` |
| Library filters changed during a scan were ignored | A minimum-duration change returned two videos instead of the one matching the new filter | Cancel obsolete queries, snapshot the current filter for each request and publish only the newest result | `filtersChangedDuringScanUseNewestRequest` |
| Same-named folders across storage volumes were merged | Two distinct Movies folders appeared as one | Include the volume identity in secondary-storage folder labels | `identicalFoldersOnDifferentVolumesStaySeparate` |

The five failures were reproduced before the fixes. Their regression tests now pass. The full suite also covers permission revocation during a query, preservation of existing favourites/playlists/resume keys, dismissal of dialogs before releasing the player, finished-video behaviour, and subtitle picker results received before player restart.

## Upgrade compatibility

The app retains its original readable MediaStore URI identities. This avoids invalidating existing favourites, playlists and saved positions while improving storage-volume grouping. The signing certificate remains unchanged. Install the new APK over the old version.

## Test boundaries

Android-framework tests run under Robolectric on API 26, 28 and 35. Lifecycle tests use a mocked ExoPlayer to control state precisely. A separate test constructs the real ExoPlayer with a generated local MP4 and verifies that opening it with autoplay disabled creates one paused media item. It does not certify hardware decoding or rendered frames.

Robolectric 4.17's default Bluetooth shadow references a newer nested class while initializing the Android 15 audio service. The real-engine startup test uses a minimal test-only Bluetooth shadow to avoid that simulator defect. Bluetooth routing and disconnection behaviour are not verified by this workaround. No production code was changed for that test compatibility issue.
