package com.sumi.vid;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 28, 35})
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.media3.common.util.UnstableApi
public class AndroidRegressionTest {
  @Before
  public void reset() {
    AppSettings.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();
    Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions("com.sumi.vid.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION");
  }

  static Switch findSwitch(View v, String label) {
    if (v instanceof Switch && label.contentEquals(v.getContentDescription())) return (Switch) v;
    if (v instanceof ViewGroup)
      for (int j = 0; j < ((ViewGroup) v).getChildCount(); j++) {
        Switch found = findSwitch(((ViewGroup) v).getChildAt(j), label);
        if (found != null) return found;
      }
    return null;
  }

  static View find(View v, String label) {
    if (label.contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
      return v;
    if (v instanceof TextView && label.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof ViewGroup)
      for (int j = 0; j < ((ViewGroup) v).getChildCount(); j++) {
        View found = find(((ViewGroup) v).getChildAt(j), label);
        if (found != null) return found;
      }
    return null;
  }

  @Test
  public void libraryStartsAndOpensSettings() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      MainActivity a = c.get();
      View gear = find(a.getWindow().getDecorView(), "Open Settings");
      assertNotNull(gear);
      gear.performClick();
      assertEquals(
          SettingsActivity.class.getName(),
          Shadows.shadowOf(a).getNextStartedActivity().getComponent().getClassName());
    }
  }

  @Test
  public void settingsTogglePersistsAndSearchFiltersRows() {
    try (ActivityController<SettingsActivity> c =
        Robolectric.buildActivity(SettingsActivity.class).setup()) {
      SettingsActivity a = c.get();
      Switch s = findSwitch(a.getWindow().getDecorView(), "Start playing automatically");
      assertNotNull(s);
      s.setChecked(false);
      assertFalse(a.prefs.getBoolean("autoplay", true));
      a.settingsSearch.setText("subtitle size");
      assertEquals(
          View.GONE,
          find(a.getWindow().getDecorView(), "Resume playback").getParent().getParent()
                  instanceof View
              ? ((View)
                      find(a.getWindow().getDecorView(), "Resume playback").getParent().getParent())
                  .getVisibility()
              : -1);
      assertEquals(View.GONE, a.noMatches.getVisibility());
    }
  }

  @Test
  public void settingsSearchSurvivesRecreation() {
    try (ActivityController<SettingsActivity> c =
        Robolectric.buildActivity(SettingsActivity.class).setup()) {
      c.get().settingsSearch.setText("subtitles");
      c.recreate();
      assertEquals("subtitles", c.get().settingsSearch.getText().toString());
    }
  }

  @Test
  public void resetKeepsPlaylistsAndHistory() {
    SharedPreferences p = AppSettings.prefs(RuntimeEnvironment.getApplication());
    p.edit()
        .putStringSet("playlists", java.util.Collections.singleton("Films"))
        .putLong("pos:movie", 12000)
        .putInt("seek_seconds", 60)
        .commit();
    AppSettings.reset(p);
    assertFalse(p.contains("seek_seconds"));
    assertEquals(12000, p.getLong("pos:movie", 0));
    assertTrue(p.getStringSet("playlists", java.util.Collections.emptySet()).contains("Films"));
  }

  @Test
  public void librarySearchSurvivesRecreation() {
    try (ActivityController<MainActivity> c =
        Robolectric.buildActivity(MainActivity.class).setup()) {
      c.get().search.setText("holiday");
      c.recreate();
      assertEquals("holiday", c.get().search.getText().toString());
    }
  }

  @Test
  public void stoppedDialogsCannotTargetReleasedPlayer() {
    try (ActivityController<Harness> c = harness()) {
      c.get().speeds();
      AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
      assertTrue(dialog.isShowing());
      c.pause().stop();
      assertFalse(dialog.isShowing());
    }
  }

  @Test
  public void lifecyclePauseDoesNotErasePlayingIntent() {
    try (ActivityController<Harness> c = harness()) {
      Harness a = c.get();
      when(a.player.getPlayWhenReady()).thenReturn(true);
      c.pause();
      when(a.player.getPlayWhenReady()).thenReturn(false);
      Bundle state = new Bundle();
      a.onSaveInstanceState(state);
      assertTrue(state.getBoolean("play"));
    }
  }

  @Test
  public void completedVideoDoesNotStartAgainOnRestore() {
    try (ActivityController<Harness> c = harness()) {
      Harness a = c.get();
      when(a.player.getPlaybackState()).thenReturn(Player.STATE_ENDED);
      when(a.player.getPlayWhenReady()).thenReturn(true);
      Bundle state = new Bundle();
      a.onSaveInstanceState(state);
      assertFalse(state.getBoolean("play"));
      assertEquals(0, state.getLong("position"));
    }
  }

  @Test
  public void subtitleResultBeforeRestartDoesNotCrash() {
    try (ActivityController<Harness> c = harness()) {
      Harness a = c.get();
      c.pause().stop();
      a.onActivityResult(
          200,
          Activity.RESULT_OK,
          new Intent().setData(Uri.parse("content://subtitles/captions.srt")));
      assertEquals("content://subtitles/captions.srt", a.subtitleUri);
      assertTrue(a.prefs.getBoolean("subtitles", false));
    }
  }

  // Robolectric 4.17's stock Bluetooth shadow references an Android 16-only nested
  // type when Android 15 initializes the audio service. No Bluetooth operations
  // are used by this startup test; keep this test-only shadow free of that type.
  @Implements(android.bluetooth.BluetoothDevice.class)
  public static class StartupBluetoothShadow {}

  @Test
  @Config(shadows = StartupBluetoothShadow.class)
  public void realEngineStartsPausedWithALocalVideo() throws Exception {
    android.content.Context context = RuntimeEnvironment.getApplication();
    AppSettings.prefs(context).edit().putBoolean("autoplay", false).commit();
    java.io.File clip = new java.io.File(context.getCacheDir(), "sample.mp4");
    try (java.io.InputStream in = getClass().getResourceAsStream("/sample.mp4")) {
      assertNotNull(in);
      java.nio.file.Files.copy(
          in, clip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    Intent intent = new Intent(context, PlayerActivity.class).setData(Uri.fromFile(clip));
    try (ActivityController<PlayerActivity> c =
        Robolectric.buildActivity(PlayerActivity.class, intent).setup()) {
      assertNotNull(c.get().player);
      assertEquals(1, c.get().player.getMediaItemCount());
      assertFalse(c.get().player.getPlayWhenReady());
    }
  }

  public static class Harness extends PlayerActivity {
    @Override
    void startPlayer() {
      if (player != null) return;
      player = mock(ExoPlayer.class);
      when(player.getCurrentMediaItemIndex()).thenReturn(index);
      when(player.getCurrentPosition()).thenReturn(12000L);
      when(player.getDuration()).thenReturn(60000L);
      when(player.getTrackSelectionParameters())
          .thenReturn(TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT);
      when(player.getPlaybackState()).thenReturn(Player.STATE_READY);
      when(player.getPlayWhenReady()).thenReturn(restorePlay);
      prepared = true;
    }
  }

  private ActivityController<Harness> harness() {
    Intent i =
        new Intent(RuntimeEnvironment.getApplication(), Harness.class)
            .setData(Uri.parse("content://videos/sample.mp4"));
    return Robolectric.buildActivity(Harness.class, i).setup();
  }

  @Test
  public void userPauseIsPreservedInSavedState() {
    try (ActivityController<Harness> c = harness()) {
      Harness a = c.get();
      when(a.player.getPlayWhenReady()).thenReturn(false);
      Bundle state = new Bundle();
      a.onSaveInstanceState(state);
      assertFalse(state.getBoolean("play", true));
    }
  }

  @Test
  public void stoppedPlayerStateIsNotLost() {
    try (ActivityController<Harness> c = harness()) {
      Harness a = c.get();
      when(a.player.getPlayWhenReady()).thenReturn(true);
      c.pause().stop();
      Bundle state = new Bundle();
      a.onSaveInstanceState(state);
      assertEquals(12000L, state.getLong("position", -1));
      assertTrue(state.getBoolean("play", false));
    }
  }

  @Test
  public void malformedIntentFinishesSafely() {
    Intent i = new Intent(RuntimeEnvironment.getApplication(), PlayerActivity.class);
    i.putStringArrayListExtra("uris", new java.util.ArrayList<>());
    try (ActivityController<PlayerActivity> c =
        Robolectric.buildActivity(PlayerActivity.class, i).setup()) {
      assertTrue(c.get().isFinishing());
    }
  }
}
