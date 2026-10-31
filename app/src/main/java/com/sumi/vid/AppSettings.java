package com.sumi.vid;

import android.content.*;
import java.util.*;

final class AppSettings {
  static final String[] KEYS = {
    "shuffle",
    "resume",
    "autonext",
    "autoplay",
    "remember_speed",
    "speed",
    "scale",
    "seek_seconds",
    "control_seconds",
    "orientation",
    "keep_awake",
    "fullscreen",
    "pause_unplug",
    "gesture_double",
    "gesture_seek",
    "gesture_volume",
    "gesture_brightness",
    "pip_enabled",
    "subtitles",
    "subtitle_size",
    "subtitle_color",
    "subtitle_background",
    "subtitle_outline",
    "subtitle_position",
    "subtitle_language",
    "audio_language",
    "thumbnails",
    "minimum_seconds",
    "show_hidden",
    "excluded_folders",
    "sort",
    "history",
    "oled",
    "accent"
  };

  static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("sumi", Context.MODE_PRIVATE);
  }

  static void reset(android.content.SharedPreferences p) {
    android.content.SharedPreferences.Editor e = p.edit();
    for (String key : KEYS) e.remove(key);
    e.apply();
  }

  static void clearHistory(android.content.SharedPreferences p) {
    android.content.SharedPreferences.Editor e = p.edit();
    for (String key : p.getAll().keySet()) if (SettingsRules.isHistoryKey(key)) e.remove(key);
    e.apply();
  }

  static String themeSignature(android.content.SharedPreferences p) {
    return p.getBoolean("oled", false) + ":" + p.getInt("accent", 0);
  }
}
