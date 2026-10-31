package com.sumi.vid;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Android-native settings. Every option maps to a preference consumed by the app. */
public class SettingsActivity extends Activity {
  SharedPreferences prefs;
  LinearLayout body;
  ScrollView scroll;
  EditText settingsSearch;
  TextView noMatches;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = AppSettings.prefs(this);
    build();
    if (state != null) {
      settingsSearch.setText(state.getString("query", ""));
      int position = state.getInt("scroll", 0);
      scroll.post(() -> scroll.scrollTo(0, position));
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("query", settingsSearch.getText().toString());
    state.putInt("scroll", scroll.getScrollY());
    super.onSaveInstanceState(state);
  }

  void build() {
    int position = scroll == null ? 0 : scroll.getScrollY();
    String query = settingsSearch == null ? "" : settingsSearch.getText().toString();
    Ui.configure(this);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Ui.BG);
    Ui.insets(root);
    setContentView(root);
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    header.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8));
    root.addView(header);
    Button back = Ui.button(this, "‹", () -> finish());
    back.setTextSize(26);
    back.setContentDescription("Back to videos");
    header.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    TextView title = Ui.text(this, "Settings", 26, Color.WHITE);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    settingsSearch = new EditText(this);
    settingsSearch.setSingleLine(true);
    settingsSearch.setTextSize(15);
    settingsSearch.setTextColor(Color.WHITE);
    settingsSearch.setHintTextColor(Ui.MUTED);
    settingsSearch.setHint("Search settings");
    settingsSearch.setContentDescription("Search settings");
    settingsSearch.setBackground(Ui.bg(Ui.CARD, Ui.dp(this, 12)));
    settingsSearch.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
    searchLp.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 8));
    root.addView(settingsSearch, searchLp);
    scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
    body = new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 32));
    scroll.addView(body);
    TextView intro = Ui.text(this, "Make Sumi Vid feel like yours.", 14, Ui.MUTED);
    body.addView(intro);
    section("Playback");
    toggle("resume", "Resume playback", "Continue from your saved position", true);
    toggle("autoplay", "Start playing automatically", "Play as soon as you open a video", true);
    toggle("shuffle", "Shuffle videos", "Randomise the next videos in the queue", false);
    toggle("autonext", "Play the next video", "Continue through the current queue", true);
    choose(
        "Playback speed",
        new String[] {"0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×", "3×"},
        new Object[] {.25f, .5f, .75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f},
        "speed",
        1f);
    toggle("remember_speed", "Remember speed", "Use your last playback speed for new videos", true);
    choose(
        "Screen fit",
        new String[] {"Fit", "Zoom to fill", "Stretch"},
        new Object[] {0, 1, 2},
        "scale",
        0);
    choose(
        "Seek interval",
        new String[] {"5 seconds", "10 seconds", "15 seconds", "30 seconds", "60 seconds"},
        new Object[] {5, 10, 15, 30, 60},
        "seek_seconds",
        10);
    choose(
        "Hide playback controls",
        new String[] {
          "After 3 seconds",
          "After 4 seconds",
          "After 6 seconds",
          "After 10 seconds",
          "Keep visible"
        },
        new Object[] {3, 4, 6, 10, 0},
        "control_seconds",
        4);
    choose(
        "Player orientation",
        new String[] {"Use phone rotation setting", "Portrait", "Landscape"},
        new Object[] {0, 1, 2},
        "orientation",
        0);
    toggle(
        "keep_awake",
        "Keep the screen awake",
        "Prevent screen timeout while video is playing",
        true);
    toggle(
        "fullscreen",
        "Immersive full screen",
        "Hide system bars; swipe from an edge to reveal them",
        true);
    toggle(
        "pause_unplug",
        "Pause when audio disconnects",
        "Pause when wired headphones or a Bluetooth route disconnects",
        true);
    section("Gestures & floating playback");
    toggle(
        "gesture_double",
        "Double-tap to seek",
        "Use your selected seek interval on either side",
        true);
    toggle(
        "gesture_seek",
        "Swipe to seek",
        "Seek horizontally; apply the position when you release",
        true);
    toggle("gesture_brightness", "Brightness gesture", "Swipe vertically on the left side", true);
    toggle("gesture_volume", "Volume gesture", "Swipe vertically on the right side", true);
    toggle("pip_enabled", "Floating video", "Show the Float control on supported phones", true);
    section("Subtitles & audio");
    toggle("subtitles", "Subtitles enabled", "Show available embedded or attached subtitles", true);
    choose(
        "Subtitle size",
        new String[] {"14 sp", "18 sp", "20 sp", "24 sp", "28 sp", "32 sp"},
        new Object[] {14, 18, 20, 24, 28, 32},
        "subtitle_size",
        20);
    choose(
        "Subtitle colour",
        new String[] {"White", "Yellow", "Mint"},
        new Object[] {Color.WHITE, 0xFFFFEB3B, 0xFFA7F36B},
        "subtitle_color",
        Color.WHITE);
    toggle(
        "subtitle_background",
        "Subtitle background",
        "Place a dark background behind the text",
        true);
    toggle("subtitle_outline", "Subtitle outline", "Improve contrast against bright scenes", true);
    choose(
        "Subtitle position",
        new String[] {"Low", "Raised", "High"},
        new Object[] {0, 1, 2},
        "subtitle_position",
        0);
    String[] languages = {"Automatic", "English", "French", "Yorùbá", "Hausa", "Igbo"};
    Object[] codes = {"", "en", "fr", "yo", "ha", "ig"};
    choose("Preferred subtitle language", languages, codes, "subtitle_language", "");
    choose("Preferred audio language", languages, codes, "audio_language", "");
    section("Library");
    toggle(
        "thumbnails",
        "Video thumbnails",
        "Load previews in the library; turn off to use less memory",
        true);
    choose(
        "Default sort",
        new String[] {"Newest first", "Name", "Longest first", "Largest first"},
        new Object[] {"Newest", "Name", "Duration", "Size"},
        "sort",
        "Newest");
    choose(
        "Minimum video length",
        new String[] {"Show all videos", "10 seconds", "30 seconds", "1 minute"},
        new Object[] {0, 10, 30, 60},
        "minimum_seconds",
        0);
    toggle(
        "show_hidden",
        "Include hidden files",
        "Include names and folder paths beginning with a dot",
        false);
    action(
        "Excluded folders",
        prefs.getStringSet("excluded_folders", Collections.emptySet()).size()
            + " folders hidden from your library",
        () -> folders());
    action(
        "Video access",
        "Manage Android permissions and selected-video access",
        () ->
            startActivity(
                new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))));
    section("Appearance");
    choose(
        "Accent colour",
        new String[] {"Sumi green", "Sky blue", "Lavender", "Amber"},
        new Object[] {0, 1, 2, 3},
        "accent",
        0);
    toggle("oled", "OLED black", "Use a pure-black background with darker cards", false);
    section("Privacy & maintenance");
    toggle(
        "history",
        "Save watch history",
        "Save watched videos and playback positions on this phone",
        true);
    action(
        "Clear watch history",
        "Remove recent watches and resume positions",
        () ->
            confirm(
                "Clear watch history?",
                "Video files, favourites and playlists will stay.",
                () -> {
                  AppSettings.clearHistory(prefs);
                  Toast.makeText(this, "Watch history cleared", Toast.LENGTH_SHORT).show();
                }));
    action(
        "Reset settings",
        "Restore defaults; keep your videos and playlists",
        () ->
            confirm(
                "Reset settings?",
                "Your favourites, playlists and watch history will stay.",
                () -> {
                  AppSettings.reset(prefs);
                  build();
                }));
    section("About Sumi Vid");
    action(
        "Sumi Vid " + BuildConfig.VERSION_NAME,
        "Built for Lord_Sumi • Android 8.0 and later",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Sumi Vid " + BuildConfig.VERSION_NAME)
                .setMessage(
                    "A personal video player. No ads, account or analytics.\n\n"
                        + "Local video metadata stays on your phone. HTTPS streams connect to the"
                        + " server you choose. File-format playback depends on your device's"
                        + " codecs.\n\n"
                        + "This is a personal beta build. Casting, a private vault, a bundled codec"
                        + " pack and a background-audio service are not included.")
                .setPositiveButton("OK", null)
                .show());
    action(
        "Open-source licences",
        "AndroidX Media3, AndroidX and other dependencies",
        () -> license());
    noMatches = Ui.text(this, "No matching settings", 16, Ui.MUTED);
    body.addView(noMatches);
    noMatches.setVisibility(View.GONE);
    settingsSearch.addTextChangedListener(
        new android.text.TextWatcher() {
          public void beforeTextChanged(CharSequence t, int a, int c, int f) {}

          public void onTextChanged(CharSequence t, int a, int b, int c) {
            filter(t.toString());
          }

          public void afterTextChanged(android.text.Editable e) {}
        });
    settingsSearch.setText(query);
    filter(query);
    scroll.post(() -> scroll.scrollTo(0, position));
  }

  void filter(String query) {
    String q = query.trim().toLowerCase(Locale.ROOT);
    TextView section = null;
    boolean sectionHasRows = false;
    int matches = 0;
    for (int j = 0; j < body.getChildCount(); j++) {
      View child = body.getChildAt(j);
      if (child == noMatches) continue;
      if ("SECTION".equals(child.getTag())) {
        if (section != null) section.setVisibility(sectionHasRows ? View.VISIBLE : View.GONE);
        section = (TextView) child;
        sectionHasRows = false;
      } else if (child.getTag() instanceof String) {
        boolean visible = ((String) child.getTag()).contains(q);
        child.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
          matches++;
          sectionHasRows = true;
        }
      } else child.setVisibility(q.isEmpty() ? View.VISIBLE : View.GONE);
    }
    if (section != null) section.setVisibility(sectionHasRows ? View.VISIBLE : View.GONE);
    noMatches.setVisibility(matches == 0 ? View.VISIBLE : View.GONE);
  }

  void section(String label) {
    TextView t = Ui.text(this, label.toUpperCase(Locale.getDefault()), 11, Ui.GREEN);
    t.setLetterSpacing(.1f);
    t.setTag("SECTION");
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, Ui.dp(this, 24), 0, Ui.dp(this, 6));
    body.addView(t, lp);
  }

  LinearLayout row() {
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
    row.setBackground(Ui.bg(Ui.CARD, Ui.dp(this, 12)));
    row.setMinimumHeight(Ui.dp(this, 72));
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
    lp.setMargins(0, 0, 0, Ui.dp(this, 4));
    body.addView(row, lp);
    return row;
  }

  void labels(LinearLayout row, String title, String detail) {
    row.setTag((title + " " + detail).toLowerCase(Locale.ROOT));
    LinearLayout text = new LinearLayout(this);
    text.setOrientation(LinearLayout.VERTICAL);
    row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
    TextView t = Ui.text(this, title, 15, Color.WHITE);
    text.addView(t);
    text.addView(Ui.text(this, detail, 12, Ui.MUTED));
  }

  void toggle(String key, String title, String detail, boolean defaultValue) {
    LinearLayout row = row();
    labels(row, title, detail);
    Switch control = new Switch(this);
    control.setChecked(prefs.getBoolean(key, defaultValue));
    control.setContentDescription(title);
    int[][] states = {new int[] {android.R.attr.state_checked}, new int[] {}};
    control.setThumbTintList(new ColorStateList(states, new int[] {Ui.GREEN, Ui.MUTED}));
    control.setTrackTintList(
        new ColorStateList(states, new int[] {(Ui.GREEN & 0x00FFFFFF) | 0x66000000, 0xFF586174}));
    row.addView(control);
    control.setOnCheckedChangeListener(
        (button, checked) -> {
          prefs.edit().putBoolean(key, checked).apply();
          if (key.equals("oled")) build();
        });
    row.setOnClickListener(v -> control.setChecked(!control.isChecked()));
  }

  Object stored(String key, Object fallback) {
    Object value = prefs.getAll().get(key);
    return value == null ? fallback : value;
  }

  void choose(String title, String[] labels, Object[] values, String key, Object fallback) {
    Object current = stored(key, fallback);
    int selected = 0;
    for (int j = 0; j < values.length; j++) if (values[j].equals(current)) selected = j;
    final int choice = selected;
    action(
        title,
        labels[choice],
        () ->
            new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(
                    labels,
                    choice,
                    (dialog, which) -> {
                      SharedPreferences.Editor e = prefs.edit();
                      Object value = values[which];
                      if (value instanceof Integer) e.putInt(key, (Integer) value);
                      else if (value instanceof Float) e.putFloat(key, (Float) value);
                      else e.putString(key, (String) value);
                      e.apply();
                      dialog.dismiss();
                      build();
                    })
                .setNegativeButton("Cancel", null)
                .show());
  }

  void action(String title, String detail, Runnable tap) {
    LinearLayout row = row();
    labels(row, title, detail);
    row.addView(Ui.text(this, "›", 24, Ui.GREEN));
    row.setOnClickListener(v -> tap.run());
  }

  void folders() {
    ArrayList<String> known =
        new ArrayList<>(prefs.getStringSet("known_folders", Collections.emptySet()));
    Collections.sort(known);
    if (known.isEmpty()) {
      new AlertDialog.Builder(this)
          .setMessage("Allow video access and refresh your library first.")
          .setPositiveButton("OK", null)
          .show();
      return;
    }
    Set<String> excluded =
        new HashSet<>(prefs.getStringSet("excluded_folders", Collections.emptySet()));
    boolean[] checked = new boolean[known.size()];
    for (int j = 0; j < known.size(); j++) checked[j] = excluded.contains(known.get(j));
    new AlertDialog.Builder(this)
        .setTitle("Hide selected folders")
        .setMultiChoiceItems(
            known.toArray(new String[0]),
            checked,
            (d, w, c) -> {
              if (c) excluded.add(known.get(w));
              else excluded.remove(known.get(w));
            })
        .setPositiveButton(
            "Save",
            (d, w) -> {
              prefs.edit().putStringSet("excluded_folders", excluded).apply();
              build();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void confirm(String title, String message, Runnable done) {
    new AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton("Confirm", (d, w) -> done.run())
        .setNegativeButton("Cancel", null)
        .show();
  }

  void license() {
    try (java.io.InputStream in = getAssets().open("APACHE-2.0.txt")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buffer = new byte[4096];
      int n;
      while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
      TextView text =
          Ui.text(
              this,
              "AndroidX and Media3 use Apache License 2.0.\n\n" + out.toString("UTF-8"),
              12,
              Color.WHITE);
      ScrollView view = new ScrollView(this);
      view.addView(text);
      new AlertDialog.Builder(this)
          .setTitle("Open-source licences")
          .setView(view)
          .setPositiveButton("Close", null)
          .show();
    } catch (java.io.IOException e) {
      Toast.makeText(this, "Licence text unavailable", Toast.LENGTH_SHORT).show();
    }
  }
}
