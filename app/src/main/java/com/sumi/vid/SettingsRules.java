package com.sumi.vid;

import java.util.*;

/** Values shared by Settings and playback; unknown stored values fall back safely. */
public final class SettingsRules {
  public static int seekSeconds(int value) {
    return oneOf(value, new int[] {5, 10, 15, 30, 60}, 10);
  }

  public static int fontSize(int value) {
    return oneOf(value, new int[] {14, 18, 20, 24, 28, 32}, 20);
  }

  public static int controllerSeconds(int value) {
    return oneOf(value, new int[] {0, 3, 4, 6, 10}, 4);
  }

  public static int fit(int value) {
    return oneOf(value, new int[] {0, 1, 2}, 0);
  }

  public static float speed(float value) {
    return Float.isNaN(value) || Float.isInfinite(value) || value < .25f || value > 3 ? 1 : value;
  }

  private static int oneOf(int value, int[] allowed, int fallback) {
    for (int x : allowed) if (x == value) return value;
    return fallback;
  }

  public static boolean visible(
      String name,
      String folder,
      long duration,
      int minimumSeconds,
      boolean showHidden,
      Set<String> excluded) {
    if (name == null || folder == null || excluded.contains(folder)) return false;
    if (!showHidden
        && (name.startsWith(".")
            || Arrays.stream(folder.split("/")).anyMatch(s -> s.startsWith(".")))) return false;
    return duration >= Math.max(0, minimumSeconds) * 1000L;
  }

  public static boolean isHistoryKey(String key) {
    return key.startsWith("seen:") || key.startsWith("pos:");
  }
}
