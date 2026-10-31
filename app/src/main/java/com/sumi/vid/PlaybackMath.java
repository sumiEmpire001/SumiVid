package com.sumi.vid;

public final class PlaybackMath {
  public static long seek(long current, long delta, long duration) {
    return Math.max(0, Math.min(Math.max(0, duration), current + delta));
  }

  public static long resume(long position, long duration) {
    return position < 0
            || (duration > 0 && position >= duration - Math.min(3000, Math.max(1, duration / 10)))
        ? 0
        : position;
  }

  public static String time(long ms) {
    long s = Math.max(0, ms) / 1000;
    return s >= 3600
        ? String.format(java.util.Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
        : String.format(java.util.Locale.US, "%d:%02d", s / 60, s % 60);
  }
}
