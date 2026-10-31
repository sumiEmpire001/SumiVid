package com.sumi.vid;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.util.Rational;
import android.view.*;
import android.widget.*;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.*;
import java.util.*;

@androidx.media3.common.util.UnstableApi
public class PlayerActivity extends Activity {
  ExoPlayer player;
  PlayerView view;
  FrameLayout root;
  LinearLayout top, controls;
  TextView title, hint;
  Button lock;
  SharedPreferences prefs;
  ArrayList<String> uris, names;
  int index;
  boolean locked = false, restorePlay = true, prepared = false;
  long restorePos = 0;
  int scale = 0;
  float speed = 1;
  String subtitleUri = null;
  AudioManager audio;
  Handler handler = new Handler(Looper.getMainLooper());
  Runnable sleeper;
  boolean destroyed = false;
  boolean lifecyclePaused;
  final ArrayList<AlertDialog> activeDialogs = new ArrayList<>();

  class PlayerDialogBuilder extends AlertDialog.Builder {
    PlayerDialogBuilder(Context context) {
      super(context);
    }

    @Override
    public AlertDialog show() {
      AlertDialog dialog = super.show();
      activeDialogs.add(dialog);
      dialog.setOnDismissListener(d -> activeDialogs.remove(dialog));
      return dialog;
    }
  }

  float downX, downY, startBrightness;
  int startVolume;
  long startPosition;
  boolean dragged = false;
  GestureDetector detector;
  long gestureSeek = -1;
  int gestureAxis = 0;
  boolean resumePending = true, receiverRegistered = false;
  AlertDialog playbackError;
  long sleepDeadline = 0;
  final BroadcastReceiver floatingControls =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
          if (player == null) return;
          int command = intent.getIntExtra("command", 0);
          if (command == 1) {
            if (player.isPlaying()) player.pause();
            else player.play();
            restorePlay = player.getPlayWhenReady();
          } else seek(command == 2 ? -seekStep() : seekStep());
          updatePip();
        }
      };

  // GestureDetector invokes performClick for confirmed single taps, separately from swipes.
  @android.annotation.SuppressLint("ClickableViewAccessibility")
  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("sumi", 0);
    audio = (AudioManager) getSystemService(AUDIO_SERVICE);
    Ui.configure(this);
    restorePlay = prefs.getBoolean("autoplay", true);
    uris = getIntent().getStringArrayListExtra("uris");
    names = getIntent().getStringArrayListExtra("names");
    index = getIntent().getIntExtra("index", 0);
    if (getIntent().hasExtra("queue")) {
      try {
        QueueCodec.Queue queue = QueueStore.read(this, getIntent().getStringExtra("queue"));
        uris = queue.uris;
        names = queue.names;
        index = queue.index;
      } catch (Exception e) {
        Toast.makeText(
                this,
                "This playlist is no longer available. Open it again from your library.",
                Toast.LENGTH_LONG)
            .show();
        finish();
        return;
      }
    }
    if (uris == null) {
      uris = new ArrayList<>();
      names = new ArrayList<>();
      Uri u = getIntent().getData();
      if (u == null) {
        finish();
        return;
      }
      uris.add(u.toString());
      names.add(displayName(u));
    }
    if (uris.isEmpty()
        || names == null
        || names.size() != uris.size()
        || uris.size() > QueueCodec.MAX_ITEMS) {
      finish();
      return;
    }
    for (int j = 0; j < uris.size(); j++) {
      if (uris.get(j) == null || names.get(j) == null) {
        finish();
        return;
      }
      String scheme = Uri.parse(uris.get(j)).getScheme();
      if (!"content".equals(scheme) && !"file".equals(scheme) && !"https".equals(scheme)) {
        Toast.makeText(this, "Unsupported video address", Toast.LENGTH_LONG).show();
        finish();
        return;
      }
    }
    if (index < 0 || index >= uris.size()) index = 0;
    speed =
        prefs.getBoolean("remember_speed", true)
            ? SettingsRules.speed(prefs.getFloat("speed", 1))
            : 1;
    scale = SettingsRules.fit(prefs.getInt("scale", 0));
    if (b != null) {
      index = b.getInt("index", index);
      restorePos = b.getLong("position");
      restorePlay = b.getBoolean("play", restorePlay);
      speed = b.getFloat("speed", 1);
      subtitleUri = b.getString("subtitle");
      sleepDeadline = b.getLong("sleepDeadline", 0);
      prepared = true;
    }
    if (index < 0 || index >= uris.size()) index = 0;
    root = new FrameLayout(this);
    root.setBackgroundColor(Color.BLACK);
    Ui.insets(root);
    setContentView(root);
    view = new PlayerView(this);
    view.setResizeMode(
        new int[] {
              AspectRatioFrameLayout.RESIZE_MODE_FIT,
              AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
              AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            [Math.max(0, Math.min(2, scale))]);
    view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING);
    view.setShowSubtitleButton(true);
    view.setShowNextButton(true);
    view.setShowPreviousButton(true);
    view.setControllerShowTimeoutMs(4000);
    root.addView(view, new FrameLayout.LayoutParams(-1, -1));
    top = new LinearLayout(this);
    top.setOrientation(LinearLayout.VERTICAL);
    top.setPadding(8, 0, 8, 0);
    top.setBackgroundColor(0xB0000000);
    FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    root.addView(top, tp);
    LinearLayout heading = new LinearLayout(this);
    top.addView(heading);
    Button back = Ui.button(this, "‹", () -> finish());
    heading.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 50), Ui.dp(this, 42)));
    title = Ui.text(this, names.get(index), 15, Color.WHITE);
    title.setMaxLines(1);
    heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
    Button more = Ui.button(this, "More", () -> menu());
    heading.addView(more, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 42)));
    HorizontalScrollView hs = new HorizontalScrollView(this);
    hs.setHorizontalScrollBarEnabled(false);
    controls = new LinearLayout(this);
    hs.addView(controls);
    top.addView(hs);
    add(controls, "−10s", () -> seek(-seekStep()));
    add(controls, "+10s", () -> seek(seekStep()));
    add(controls, "Speed", () -> speeds());
    add(controls, "Fit", () -> aspect());
    add(
        controls,
        "Rotate",
        () ->
            setRequestedOrientation(
                getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                    ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    add(controls, "Float", () -> pip());
    lock = Ui.button(this, "Lock", () -> toggleLock());
    FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(
            Ui.dp(this, 72), Ui.dp(this, 42), Gravity.END | Gravity.CENTER_VERTICAL);
    lp.rightMargin = Ui.dp(this, 8);
    root.addView(lock, lp);
    hint = Ui.text(this, "", 18, Color.WHITE);
    hint.setGravity(17);
    hint.setBackground(Ui.bg(0xB0000000, 20));
    hint.setVisibility(View.GONE);
    FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
    root.addView(hint, hp);
    view.setControllerVisibilityListener(
        (PlayerView.ControllerVisibilityListener)
            v -> {
              if (!locked && !isInPictureInPictureMode()) {
                top.setVisibility(v);
                lock.setVisibility(v);
              }
            });
    detector =
        new GestureDetector(
            this,
            new GestureDetector.SimpleOnGestureListener() {
              @Override
              public boolean onDown(android.view.MotionEvent e) {
                return true;
              }

              @Override
              public boolean onSingleTapConfirmed(android.view.MotionEvent e) {
                if (!locked) view.performClick();
                return true;
              }

              @Override
              public boolean onDoubleTap(android.view.MotionEvent e) {
                if (!locked && prefs.getBoolean("gesture_double", true))
                  seek(e.getX() < view.getWidth() / 2f ? -seekStep() : seekStep());
                return true;
              }
            });
    view.setOnTouchListener((v, e) -> gesture(e));
    IntentFilter floatingFilter = new IntentFilter("com.sumi.vid.FLOAT_CONTROL");
    androidx.core.content.ContextCompat.registerReceiver(
        this,
        floatingControls,
        floatingFilter,
        androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    receiverRegistered = true;
    if (sleepDeadline > 0) scheduleSleep();
    if (b != null && b.getBoolean("locked", false)) toggleLock();
    startPlayer();
  }

  String displayName(Uri u) {
    try (Cursor c =
        getContentResolver()
            .query(u, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception ignored) {
    }
    String n = u.getLastPathSegment();
    return n == null ? "Video" : n;
  }

  void add(LinearLayout p, String text, Runnable r) {
    Button b = Ui.button(this, text, r);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 38));
    lp.setMargins(3, 2, 3, 4);
    p.addView(b, lp);
  }

  long seekStep() {
    return SettingsRules.seekSeconds(prefs.getInt("seek_seconds", 10)) * 1000L;
  }

  void markSeen() {
    if (prefs.getBoolean("history", true))
      prefs.edit().putLong("seen:" + uris.get(index), System.currentTimeMillis()).apply();
  }

  void displaySettings() {
    Ui.configure(this);
    Ui.refreshButtons(root);
    scale = SettingsRules.fit(prefs.getInt("scale", 0));
    if (prefs.getBoolean("remember_speed", true))
      speed = SettingsRules.speed(prefs.getFloat("speed", 1));
    view.setResizeMode(
        new int[] {
              AspectRatioFrameLayout.RESIZE_MODE_FIT,
              AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
              AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            [scale]);
    view.setControllerShowTimeoutMs(
        SettingsRules.controllerSeconds(prefs.getInt("control_seconds", 4)) * 1000);
    if (controls != null) {
      ((Button) controls.getChildAt(0))
          .setText(getString(R.string.seek_backward, seekStep() / 1000));
      ((Button) controls.getChildAt(1))
          .setText(getString(R.string.seek_forward, seekStep() / 1000));
      controls
          .getChildAt(5)
          .setVisibility(prefs.getBoolean("pip_enabled", true) ? View.VISIBLE : View.GONE);
    }
    int orientation = prefs.getInt("orientation", 0);
    setRequestedOrientation(
        orientation == 1
            ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            : orientation == 2
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
    if (Build.VERSION.SDK_INT >= 30) {
      WindowInsetsController bars = getWindow().getInsetsController();
      if (bars != null) {
        if (prefs.getBoolean("fullscreen", true)) {
          bars.hide(WindowInsets.Type.systemBars());
          bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else bars.show(WindowInsets.Type.systemBars());
      }
    } else
      getWindow()
          .getDecorView()
          .setSystemUiVisibility(
              prefs.getBoolean("fullscreen", true)
                  ? View.SYSTEM_UI_FLAG_FULLSCREEN
                      | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                      | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                  : View.SYSTEM_UI_FLAG_VISIBLE);
    SubtitleView subtitles = view.getSubtitleView();
    if (subtitles != null) {
      subtitles.setApplyEmbeddedStyles(false);
      subtitles.setApplyEmbeddedFontSizes(false);
      subtitles.setFixedTextSize(
          android.util.TypedValue.COMPLEX_UNIT_SP,
          SettingsRules.fontSize(prefs.getInt("subtitle_size", 20)));
      subtitles.setStyle(
          new CaptionStyleCompat(
              prefs.getInt("subtitle_color", android.graphics.Color.WHITE),
              prefs.getBoolean("subtitle_background", true)
                  ? 0x99000000
                  : android.graphics.Color.TRANSPARENT,
              android.graphics.Color.TRANSPARENT,
              prefs.getBoolean("subtitle_outline", true)
                  ? CaptionStyleCompat.EDGE_TYPE_OUTLINE
                  : CaptionStyleCompat.EDGE_TYPE_NONE,
              android.graphics.Color.BLACK,
              android.graphics.Typeface.DEFAULT_BOLD));
      subtitles.setBottomPaddingFraction(
          new float[] {.08f, .15f, .22f}
              [Math.max(0, Math.min(2, prefs.getInt("subtitle_position", 0)))]);
    }
    if (player != null) {
      player.setPlaybackSpeed(speed);
      player.setShuffleModeEnabled(prefs.getBoolean("shuffle", false));
      player.setHandleAudioBecomingNoisy(prefs.getBoolean("pause_unplug", true));
      player.setPauseAtEndOfMediaItems(
          !prefs.getBoolean("autonext", true) && player.getRepeatMode() == Player.REPEAT_MODE_OFF);
      String text = prefs.getString("subtitle_language", ""),
          audio = prefs.getString("audio_language", "");
      player.setTrackSelectionParameters(
          player
              .getTrackSelectionParameters()
              .buildUpon()
              .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !prefs.getBoolean("subtitles", true))
              .setPreferredTextLanguage(text.isEmpty() ? null : text)
              .setPreferredAudioLanguage(audio.isEmpty() ? null : audio)
              .build());
      if (player.isPlaying() && prefs.getBoolean("keep_awake", true))
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
      else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
  }

  void startPlayer() {
    if (player != null) return;
    resumePending = true;
    player =
        new ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(seekStep())
            .setSeekForwardIncrementMs(seekStep())
            .build();
    player.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build(),
        true);
    view.setPlayer(player);
    player.addListener(
        new Player.Listener() {
          @Override
          public void onRepeatModeChanged(int repeatMode) {
            player.setPauseAtEndOfMediaItems(
                repeatMode == Player.REPEAT_MODE_OFF && !prefs.getBoolean("autonext", true));
          }

          @Override
          public void onIsPlayingChanged(boolean playing) {
            if (BuildConfig.DEBUG) android.util.Log.i("SumiPlayback", "playing=" + playing);
            if (isInPictureInPictureMode()) updatePip();
            if (playing && prefs.getBoolean("keep_awake", true))
              getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
          }

          @Override
          public void onPlayerError(PlaybackException e) {
            if (isFinishing()
                || isDestroyed()
                || (playbackError != null && playbackError.isShowing())) return;
            playbackError =
                new PlayerDialogBuilder(PlayerActivity.this)
                    .setTitle("Couldn't play this video")
                    .setMessage(
                        "The file may be unavailable or use a codec this phone doesn't support. For"
                            + " a stream, check the connection and direct video URL.\n\n"
                            + e.getErrorCodeName())
                    .setPositiveButton(
                        "Retry",
                        (d, w) -> {
                          if (player != null) {
                            player.prepare();
                            player.play();
                          }
                        })
                    .setNegativeButton("Close", (d, w) -> finish())
                    .show();
          }

          @Override
          public void onMediaItemTransition(MediaItem item, int reason) {
            if (item != null) {
              int newIndex = player.getCurrentMediaItemIndex();
              if (newIndex != index) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                    && prefs.getBoolean("history", true))
                  prefs.edit().putLong("pos:" + uris.get(index), 0).apply();
                index = newIndex;
                subtitleUri = prefs.getString("subtitle:" + uris.get(index), null);
                resumePending = false;
              }
              title.setText(names.get(index));
              markSeen();
            }
          }

          @Override
          public void onPlaybackStateChanged(int state) {
            if (state == Player.STATE_READY && resumePending) {
              resumePending = false;
              long current = player.getCurrentPosition(), duration = player.getDuration();
              if (duration > 0) {
                long normalized = PlaybackMath.resume(current, duration);
                if (current != normalized) player.seekTo(normalized);
              }
            }
            if (state == Player.STATE_ENDED && prefs.getBoolean("history", true)) {
              prefs.edit().putLong("pos:" + uris.get(index), 0).apply();
            }
          }
        });
    ArrayList<MediaItem> items = new ArrayList<>();
    for (int j = 0; j < uris.size(); j++)
      items.add(
          item(
              j,
              j == index && subtitleUri != null
                  ? subtitleUri
                  : prefs.getString("subtitle:" + uris.get(j), null)));
    long p =
        prepared
            ? restorePos
            : (prefs.getBoolean("resume", true) && prefs.getBoolean("history", true)
                ? prefs.getLong("pos:" + uris.get(index), 0)
                : 0);
    player.setMediaItems(items, index, Math.max(0, p));
    player.setPauseAtEndOfMediaItems(!prefs.getBoolean("autonext", true));
    player.setPlaybackSpeed(speed);
    displaySettings();
    player.prepare();
    player.setPlayWhenReady(restorePlay);
    markSeen();
    prepared = true;
    handler.removeCallbacks(checkpoint);
    handler.postDelayed(checkpoint, 5000);
  }

  MediaItem item(int i, String sub) {
    MediaItem.Builder b =
        new MediaItem.Builder()
            .setUri(uris.get(i))
            .setMediaId(uris.get(i))
            .setMediaMetadata(new MediaMetadata.Builder().setTitle(names.get(i)).build());
    if (sub != null) {
      String n = displayName(Uri.parse(sub)).toLowerCase(Locale.ROOT);
      String mime =
          n.endsWith(".vtt")
              ? MimeTypes.TEXT_VTT
              : n.endsWith(".ssa") || n.endsWith(".ass")
                  ? MimeTypes.TEXT_SSA
                  : MimeTypes.APPLICATION_SUBRIP;
      MediaItem.SubtitleConfiguration s =
          new MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub))
              .setMimeType(mime)
              .setLanguage("und")
              .setLabel("External subtitles")
              .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
              .build();
      b.setSubtitleConfigurations(Collections.singletonList(s));
    }
    return b.build();
  }

  final Runnable checkpoint =
      new Runnable() {
        public void run() {
          if (player != null) {
            save();
            handler.postDelayed(this, 5000);
          }
        }
      };

  void save() {
    if (player == null || !prefs.getBoolean("history", true)) return;
    int i = player.getCurrentMediaItemIndex();
    if (i >= 0 && i < uris.size())
      prefs
          .edit()
          .putLong(
              "pos:" + uris.get(i),
              player.getPlaybackState() == Player.STATE_ENDED ? 0 : player.getCurrentPosition())
          .apply();
  }

  void seek(long delta) {
    if (player == null) return;
    long d = player.getDuration();
    if (d <= 0) {
      notice("Seeking isn't available yet");
      return;
    }
    player.seekTo(PlaybackMath.seek(player.getCurrentPosition(), delta, d));
    notice((delta < 0 ? "−" : "+") + Math.abs(delta / 1000) + " seconds");
  }

  void speeds() {
    String[] opts = {"0.25×", "0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×", "3×"};
    float[] values = {.25f, .5f, .75f, 1, 1.25f, 1.5f, 1.75f, 2, 3};
    new PlayerDialogBuilder(this)
        .setTitle("Playback speed")
        .setItems(
            opts,
            (d, w) -> {
              speed = values[w];
              player.setPlaybackSpeed(speed);
              if (prefs.getBoolean("remember_speed", true))
                prefs.edit().putFloat("speed", speed).apply();
              notice(opts[w]);
            })
        .show();
  }

  void aspect() {
    scale = (scale + 1) % 3;
    prefs.edit().putInt("scale", scale).apply();
    view.setResizeMode(
        new int[] {
              AspectRatioFrameLayout.RESIZE_MODE_FIT,
              AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
              AspectRatioFrameLayout.RESIZE_MODE_FILL
            }
            [scale]);
    notice(new String[] {"Fit to screen", "Zoom to fill", "Stretch"}[scale]);
  }

  void toggleLock() {
    locked = !locked;
    view.setUseController(!locked);
    top.setVisibility(locked ? View.GONE : View.VISIBLE);
    lock.setText(locked ? "Unlock" : "Lock");
    if (!locked) view.showController();
  }

  void menu() {
    new PlayerDialogBuilder(this)
        .setTitle("Playback tools")
        .setItems(
            new String[] {
              "Audio tracks",
              "Subtitle tracks / off",
              "Load subtitle file",
              "Repeat mode",
              "Sleep timer",
              "Video information",
              "Gesture guide",
              "Settings",
              "Remove external subtitles"
            },
            (d, w) -> {
              if (w == 7) {
                startActivity(new Intent(this, SettingsActivity.class));
                return;
              }
              if (w == 8) {
                subtitleUri = null;
                prefs.edit().remove("subtitle:" + uris.get(index)).apply();
                long position = player.getCurrentPosition();
                player.replaceMediaItem(index, item(index, null));
                player.seekTo(index, position);
                notice("External subtitles removed");
                return;
              }
              switch (w) {
                case 0:
                  tracks(C.TRACK_TYPE_AUDIO);
                  break;
                case 1:
                  tracks(C.TRACK_TYPE_TEXT);
                  break;
                case 2:
                  Intent s = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                  s.setType("*/*");
                  s.addCategory(Intent.CATEGORY_OPENABLE);
                  s.addFlags(
                      Intent.FLAG_GRANT_READ_URI_PERMISSION
                          | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                  startActivityForResult(s, 200);
                  break;
                case 3:
                  new PlayerDialogBuilder(this)
                      .setTitle("Repeat")
                      .setItems(
                          new String[] {"Off", "Repeat this video", "Repeat playlist"},
                          (a, b) ->
                              player.setRepeatMode(
                                  new int[] {
                                        Player.REPEAT_MODE_OFF,
                                        Player.REPEAT_MODE_ONE,
                                        Player.REPEAT_MODE_ALL
                                      }
                                      [b]))
                      .show();
                  break;
                case 4:
                  sleep();
                  break;
                case 5:
                  Format f = player.getVideoFormat();
                  new PlayerDialogBuilder(this)
                      .setTitle(names.get(index))
                      .setMessage(
                          f == null
                              ? "Video information isn't available yet."
                              : "Resolution: "
                                  + f.width
                                  + " × "
                                  + f.height
                                  + "\nCodec: "
                                  + f.codecs
                                  + "\nFrame rate: "
                                  + f.frameRate
                                  + "\nDuration: "
                                  + PlaybackMath.time(player.getDuration()))
                      .setPositiveButton("OK", null)
                      .show();
                  break;
                case 6:
                  new PlayerDialogBuilder(this)
                      .setTitle("Make yourself comfortable")
                      .setMessage(
                          "Double-tap left/right: your chosen seek interval\n"
                              + "Swipe horizontally: seek\n"
                              + "Swipe up/down on left: brightness\n"
                              + "Swipe up/down on right: volume\n"
                              + "Lock: block accidental touches\n"
                              + "Float: picture-in-picture\n\n"
                              + "Open More → Load subtitle file for SRT, VTT, SSA or ASS"
                              + " subtitles.")
                      .setPositiveButton("Got it", null)
                      .show();
                  break;
              }
            })
        .show();
  }

  void tracks(int type) {
    ArrayList<String> labels = new ArrayList<>();
    ArrayList<TrackSelectionOverride> overrides = new ArrayList<>();
    labels.add(type == C.TRACK_TYPE_TEXT ? "Off" : "Automatic");
    overrides.add(null);
    for (Tracks.Group g : player.getCurrentTracks().getGroups()) {
      if (g.getType() != type) continue;
      for (int t = 0; t < g.length; t++) {
        if (!g.isTrackSupported(t)) continue;
        Format f = g.getTrackFormat(t);
        labels.add(
            (f.label == null ? (type == C.TRACK_TYPE_AUDIO ? "Audio" : "Subtitles") : f.label)
                + " • "
                + (f.language == null ? "Track " + (t + 1) : f.language));
        overrides.add(new TrackSelectionOverride(g.getMediaTrackGroup(), t));
      }
    }
    new PlayerDialogBuilder(this)
        .setTitle(type == C.TRACK_TYPE_AUDIO ? "Audio tracks" : "Subtitle tracks")
        .setItems(
            labels.toArray(new String[0]),
            (d, w) -> {
              TrackSelectionParameters.Builder b =
                  player
                      .getTrackSelectionParameters()
                      .buildUpon()
                      .clearOverridesOfType(type)
                      .setTrackTypeDisabled(type, type == C.TRACK_TYPE_TEXT && w == 0);
              if (w > 0) b.setOverrideForType(overrides.get(w));
              if (type == C.TRACK_TYPE_TEXT) prefs.edit().putBoolean("subtitles", w > 0).apply();
              player.setTrackSelectionParameters(b.build());
            })
        .show();
  }

  void sleep() {
    String[] choices = {"Off", "15 minutes", "30 minutes", "60 minutes", "90 minutes"};
    new PlayerDialogBuilder(this)
        .setTitle(
            "Sleep timer"
                + (sleepDeadline > 0
                    ? " • "
                        + Math.max(1, (sleepDeadline - System.currentTimeMillis()) / 60000)
                        + " min left"
                    : ""))
        .setItems(
            choices,
            (d, w) -> {
              sleepDeadline =
                  w == 0
                      ? 0
                      : System.currentTimeMillis() + new int[] {0, 15, 30, 60, 90}[w] * 60000L;
              scheduleSleep();
              notice("Sleep timer: " + choices[w]);
            })
        .show();
  }

  void scheduleSleep() {
    if (sleeper != null) handler.removeCallbacks(sleeper);
    if (sleepDeadline == 0) return;
    sleeper =
        () -> {
          sleepDeadline = 0;
          restorePlay = false;
          if (player != null) player.pause();
          notice("Sleep timer: playback paused");
        };
    handler.postDelayed(sleeper, Math.max(0, sleepDeadline - System.currentTimeMillis()));
  }

  RemoteAction floatingAction(String label, int icon, int command) {
    Intent intent =
        new Intent("com.sumi.vid.FLOAT_CONTROL")
            .setPackage(getPackageName())
            .putExtra("command", command);
    PendingIntent pending =
        PendingIntent.getBroadcast(
            this,
            command,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    return new RemoteAction(Icon.createWithResource(this, icon), label, label, pending);
  }

  PictureInPictureParams pipParams() {
    PictureInPictureParams.Builder params =
        new PictureInPictureParams.Builder().setAspectRatio(new Rational(16, 9));
    Rect bounds = new Rect();
    if (view.getGlobalVisibleRect(bounds)) params.setSourceRectHint(bounds);
    if (Build.VERSION.SDK_INT >= 31) params.setAutoEnterEnabled(false);
    boolean playing = player != null && player.isPlaying();
    params.setActions(
        Arrays.asList(
            floatingAction(
                "Back " + seekStep() / 1000 + " seconds", android.R.drawable.ic_media_rew, 2),
            floatingAction(
                playing ? "Pause" : "Play",
                playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                1),
            floatingAction(
                "Forward " + seekStep() / 1000 + " seconds", android.R.drawable.ic_media_ff, 3)));
    return params.build();
  }

  void updatePip() {
    if (destroyed || isFinishing()) return;
    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
      setPictureInPictureParams(pipParams());
  }

  void pip() {
    if (!prefs.getBoolean("pip_enabled", true)) {
      notice("Floating video is disabled in Settings");
      return;
    }
    if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
      notice("Floating playback isn't supported on this device");
      return;
    }
    try {
      enterPictureInPictureMode(pipParams());
    } catch (IllegalStateException | IllegalArgumentException e) {
      notice("Enable picture-in-picture for Sumi Vid in Android settings");
    }
  }

  @Override
  public void onPictureInPictureModeChanged(boolean pip, Configuration c) {
    super.onPictureInPictureModeChanged(pip, c);
    top.setVisibility(pip || locked ? View.GONE : View.VISIBLE);
    lock.setVisibility(pip ? View.GONE : View.VISIBLE);
    view.setUseController(!pip && !locked);
    if (pip) hint.setVisibility(View.GONE);
  }

  boolean gesture(MotionEvent e) {
    if (locked) return true;
    detector.onTouchEvent(e);
    if (player == null) return true;
    if (e.getAction() == MotionEvent.ACTION_DOWN) {
      downX = e.getX();
      downY = e.getY();
      startPosition = player.getCurrentPosition();
      startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
      startBrightness = getWindow().getAttributes().screenBrightness;
      if (startBrightness < 0) startBrightness = .5f;
      dragged = false;
      gestureSeek = -1;
      gestureAxis = 0;
    }
    if (e.getAction() == MotionEvent.ACTION_MOVE) {
      float dx = e.getX() - downX, dy = downY - e.getY();
      if (Math.abs(dx) > Ui.dp(this, 24) || Math.abs(dy) > Ui.dp(this, 24)) dragged = true;
      if (dragged) {
        if (gestureAxis == 0) gestureAxis = Math.abs(dx) > Math.abs(dy) ? 1 : 2;
        if (gestureAxis == 1 && prefs.getBoolean("gesture_seek", true)) {
          long duration = player.getDuration();
          if (duration > 0) {
            long pos =
                PlaybackMath.seek(
                    startPosition, (long) (dx / Math.max(1, view.getWidth()) * 120000), duration);
            gestureSeek = pos;
            notice(PlaybackMath.time(pos) + " / " + PlaybackMath.time(duration));
          }
        } else if (gestureAxis == 2
            && downX < view.getWidth() / 2f
            && prefs.getBoolean("gesture_brightness", true)) {
          float b =
              Math.max(.02f, Math.min(1, startBrightness + dy / Math.max(1, view.getHeight())));
          WindowManager.LayoutParams a = getWindow().getAttributes();
          a.screenBrightness = b;
          getWindow().setAttributes(a);
          notice("Brightness " + Math.round(b * 100) + "%");
        } else if (gestureAxis == 2
            && downX >= view.getWidth() / 2f
            && prefs.getBoolean("gesture_volume", true)) {
          int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
          int vol =
              Math.max(
                  0,
                  Math.min(
                      max, startVolume + Math.round(dy / Math.max(1, view.getHeight()) * max)));
          audio.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
          notice("Volume " + Math.round(vol * 100f / max) + "%");
        }
      }
    }
    if (e.getAction() == MotionEvent.ACTION_UP && gestureSeek >= 0) {
      player.seekTo(gestureSeek);
      gestureSeek = -1;
    }
    if (e.getAction() == MotionEvent.ACTION_CANCEL) gestureSeek = -1;
    return true;
  }

  Runnable hideHint =
      () -> {
        if (hint != null) hint.setVisibility(View.GONE);
      };

  void notice(String text) {
    hint.setText(text);
    hint.setVisibility(View.VISIBLE);
    handler.removeCallbacks(hideHint);
    handler.postDelayed(hideHint, 1200);
  }

  @Override
  protected void onActivityResult(int r, int c, Intent d) {
    super.onActivityResult(r, c, d);
    if (r == 200 && c == RESULT_OK && d != null && d.getData() != null) {
      Uri u = d.getData();
      String n = displayName(u).toLowerCase(Locale.ROOT);
      if (!(n.endsWith(".srt") || n.endsWith(".vtt") || n.endsWith(".ssa") || n.endsWith(".ass"))) {
        notice("Choose an SRT, VTT, SSA or ASS subtitle file");
        return;
      }
      try {
        getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        prefs.edit().putString("subtitle:" + uris.get(index), u.toString()).apply();
      } catch (SecurityException ignored) {
      }
      subtitleUri = u.toString();
      prefs.edit().putBoolean("subtitles", true).apply();
      if (player != null) {
        long p = player.getCurrentPosition();
        player.replaceMediaItem(index, item(index, subtitleUri));
        player.seekTo(index, p);
        player.setPlayWhenReady(restorePlay);
        player.setTrackSelectionParameters(
            player
                .getTrackSelectionParameters()
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build());
      }
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (view != null && player == null) startPlayer();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (view != null) displaySettings();
    lifecyclePaused = false;
    if (player != null && restorePlay) player.play();
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (player != null && !isInPictureInPictureMode()) {
      if (!lifecyclePaused) restorePlay = player.getPlayWhenReady();
      lifecyclePaused = true;
      player.pause();
    }
  }

  @Override
  protected void onStop() {
    super.onStop();
    for (AlertDialog dialog : new ArrayList<>(activeDialogs)) dialog.dismiss();
    playbackError = null;
    if (player != null) {
      handler.removeCallbacks(checkpoint);
      save();
      capturePlaybackState();
      view.setPlayer(null);
      player.release();
      player = null;
    }
  }

  void capturePlaybackState() {
    if (player == null) return;
    index = player.getCurrentMediaItemIndex();
    restorePos =
        player.getPlaybackState() == Player.STATE_ENDED
            ? 0
            : Math.max(0, player.getCurrentPosition());
    if (player.getPlaybackState() == Player.STATE_ENDED) restorePlay = false;
    else if (!lifecyclePaused) restorePlay = player.getPlayWhenReady();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    capturePlaybackState();
    b.putInt("index", index);
    b.putLong("position", restorePos);
    b.putBoolean("play", restorePlay);
    b.putBoolean("locked", locked);
    b.putLong("sleepDeadline", sleepDeadline);
    b.putFloat("speed", speed);
    b.putString("subtitle", subtitleUri);
    super.onSaveInstanceState(b);
  }

  @Override
  public void onBackPressed() {
    if (locked) {
      notice("Tap Unlock first");
      return;
    }
    super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    destroyed = true;
    if (receiverRegistered) {
      unregisterReceiver(floatingControls);
      receiverRegistered = false;
    }
    handler.removeCallbacksAndMessages(null);
    if (player != null) {
      save();
      player.release();
      player = null;
    }
    super.onDestroy();
  }
}
