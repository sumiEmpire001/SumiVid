package com.sumi.vid;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class SettingsRulesTest {
  @Test
  public void rejectsBrokenPlaybackValues() {
    assertEquals(10, SettingsRules.seekSeconds(-30));
    assertEquals(10, SettingsRules.seekSeconds(9999));
    assertEquals(20, SettingsRules.fontSize(Integer.MAX_VALUE));
    assertEquals(4, SettingsRules.controllerSeconds(-1));
    assertEquals(0, SettingsRules.fit(7));
  }

  @Test
  public void keepControlsVisibleIsValid() {
    assertEquals(0, SettingsRules.controllerSeconds(0));
    assertEquals(60, SettingsRules.seekSeconds(60));
  }

  @Test
  public void brokenSpeedNeverReachesPlayer() {
    assertEquals(1, SettingsRules.speed(Float.NaN), 0);
    assertEquals(1, SettingsRules.speed(Float.POSITIVE_INFINITY), 0);
    assertEquals(1, SettingsRules.speed(-1), 0);
    assertEquals(1.75, SettingsRules.speed(1.75f), 0);
  }

  @Test
  public void hidesDotFilesAndNestedHiddenFolders() {
    assertFalse(
        SettingsRules.visible(".preview.mp4", "Movies/", 60000, 0, false, Collections.emptySet()));
    assertFalse(
        SettingsRules.visible(
            "video.mp4", "Movies/.cache/clips/", 60000, 0, false, Collections.emptySet()));
    assertTrue(
        SettingsRules.visible(
            ".preview.mp4", "Movies/.cache/", 60000, 0, true, Collections.emptySet()));
  }

  @Test
  public void filterBoundariesUseMilliseconds() {
    assertTrue(SettingsRules.visible("v.mp4", "Movies/", 10000, 10, false, Collections.emptySet()));
    assertFalse(SettingsRules.visible("v.mp4", "Movies/", 9999, 10, false, Collections.emptySet()));
    assertTrue(SettingsRules.visible("v.mp4", "Movies/", 0, 0, false, Collections.emptySet()));
  }

  @Test
  public void exclusionsDoNotMergeSameNamedFolders() {
    assertFalse(
        SettingsRules.visible(
            "v.mp4",
            "Movies/Downloads/",
            1000,
            0,
            true,
            Collections.singleton("Movies/Downloads/")));
    assertTrue(
        SettingsRules.visible(
            "v.mp4",
            "Other/Downloads/",
            1000,
            0,
            true,
            Collections.singleton("Movies/Downloads/")));
  }

  @Test
  public void clearingHistoryPreservesOtherData() {
    assertTrue(SettingsRules.isHistoryKey("seen:content://video/1"));
    assertTrue(SettingsRules.isHistoryKey("pos:content://video/1"));
    assertFalse(SettingsRules.isHistoryKey("playlist:seen:movies"));
    assertFalse(SettingsRules.isHistoryKey("fav:pos:video"));
    assertFalse(SettingsRules.isHistoryKey("speed"));
  }
}
