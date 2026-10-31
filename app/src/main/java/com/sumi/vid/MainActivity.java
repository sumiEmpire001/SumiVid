package com.sumi.vid;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.*;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.Size;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
  static class Video {
    String uri, name, folder;
    long duration, size, date;

    Video(String u, String n, String f, long d, long s, long t) {
      uri = u;
      name = n;
      folder = f;
      duration = d;
      size = s;
      date = t;
    }
  }

  final ArrayList<Video> videos = new ArrayList<>();
  final ExecutorService loader = Executors.newSingleThreadExecutor();
  ListView list;
  final ArrayList<Row> rows = new ArrayList<>();
  final Map<String, Button> tabButtons = new HashMap<>();
  BaseAdapter adapter;
  final LruCache<String, Bitmap> thumbnails =
      new LruCache<String, Bitmap>(8 * 1024 * 1024) {
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getByteCount();
        }
      };
  final ThreadPoolExecutor thumbnailLoader =
      new ThreadPoolExecutor(
          2,
          2,
          20,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(24),
          new ThreadPoolExecutor.DiscardOldestPolicy());
  volatile int scanGeneration = 0;
  CancellationSignal scanSignal;
  String appliedTheme = "";
  boolean scanning;

  static class Row {
    String title, detail, symbol;
    Video video;
    Runnable tap, more;

    Row(String t, String d, String symbol, Video v, Runnable tap, Runnable more) {
      title = t;
      detail = d;
      this.symbol = symbol;
      video = v;
      this.tap = tap;
      this.more = more;
    }
  }

  static class Holder {
    TextView title, detail, symbol;
    ImageView image;
    Button more;
    ProgressBar progress;
    FrameLayout artwork;
  }

  TextView summary;
  EditText search;
  SharedPreferences prefs;
  String tab = "All videos", folder = "", sort = "Newest";
  volatile boolean alive = true;

  @Override
  public void onCreate(Bundle b) {
    super.onCreate(b);
    prefs = getSharedPreferences("sumi", 0);
    Ui.configure(this);
    appliedTheme = AppSettings.themeSignature(prefs);
    build();
    sort = prefs.getString("sort", "Newest");
    if (b != null) {
      tab = b.getString("tab", "All videos");
      folder = b.getString("folder", "");
      search.setText(b.getString("query", ""));
    }
    render();
  }

  void build() {
    tabButtons.clear();
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(Ui.BG);
    Ui.insets(root);
    setContentView(root);
    LinearLayout content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    content.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), 0);
    root.addView(content, new LinearLayout.LayoutParams(-1, -1));
    LinearLayout heading = new LinearLayout(this);
    heading.setGravity(Gravity.CENTER_VERTICAL);
    content.addView(heading);
    TextView title = Ui.text(this, "Sumi Vid", 28, Color.WHITE);
    title.setTypeface(null, android.graphics.Typeface.BOLD);
    heading.addView(title, new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1));
    Button openButton = Ui.button(this, "＋", () -> pick());
    openButton.setTextSize(22);
    openButton.setContentDescription("Open a video file");
    heading.addView(openButton, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
    Button settings =
        Ui.button(this, "⚙", () -> startActivity(new Intent(this, SettingsActivity.class)));
    settings.setTextSize(20);
    settings.setContentDescription("Open Settings");
    LinearLayout.LayoutParams settingsLp =
        new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
    settingsLp.setMargins(Ui.dp(this, 6), 0, 0, 0);
    heading.addView(settings, settingsLp);
    Button menuButton = Ui.button(this, "⋮", () -> options());
    menuButton.setTextSize(22);
    menuButton.setContentDescription("Library options");
    LinearLayout.LayoutParams menuLp =
        new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
    menuLp.setMargins(Ui.dp(this, 6), 0, 0, 0);
    heading.addView(menuButton, menuLp);
    summary = Ui.text(this, "Your personal cinema, anywhere.", 13, Ui.MUTED);
    content.addView(summary);
    search = new EditText(this);
    search.setSingleLine(true);
    search.setTextColor(Color.WHITE);
    search.setHintTextColor(Ui.MUTED);
    search.setHint("Search your videos…");
    search.setTextSize(15);
    search.setBackground(Ui.bg(Ui.CARD, Ui.dp(this, 14)));
    search.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
    search.setContentDescription("Search videos by name or folder");
    LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 48));
    searchLp.setMargins(0, Ui.dp(this, 12), 0, Ui.dp(this, 8));
    content.addView(search, searchLp);
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int a, int c, int f) {}

          public void onTextChanged(CharSequence s, int a, int b, int c) {
            render();
          }

          public void afterTextChanged(Editable e) {}
        });
    HorizontalScrollView scroll = new HorizontalScrollView(this);
    scroll.setHorizontalScrollBarEnabled(false);
    LinearLayout tabs = new LinearLayout(this);
    scroll.addView(tabs);
    content.addView(scroll);
    for (String t : new String[] {"All videos", "Folders", "Favourites", "Recent", "Playlists"}) {
      Button button =
          Ui.button(
              this,
              t,
              () -> {
                tab = t;
                folder = "";
                render();
              });
      LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, Ui.dp(this, 44));
      params.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 8));
      tabs.addView(button, params);
      tabButtons.put(t, button);
    }
    list = new ListView(this);
    list.setDivider(null);
    list.setCacheColorHint(Color.TRANSPARENT);
    list.setClipToPadding(false);
    list.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 12));
    content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    adapter =
        new BaseAdapter() {
          public int getCount() {
            return rows.size();
          }

          public Object getItem(int p) {
            return rows.get(p);
          }

          public long getItemId(int p) {
            return p;
          }

          public View getView(int position, View recycled, ViewGroup parent) {
            Holder h;
            LinearLayout outer;
            if (recycled == null) {
              outer = new LinearLayout(MainActivity.this);
              outer.setPadding(0, Ui.dp(MainActivity.this, 4), 0, Ui.dp(MainActivity.this, 4));
              LinearLayout card = new LinearLayout(MainActivity.this);
              card.setGravity(Gravity.CENTER_VERTICAL);
              card.setPadding(
                  Ui.dp(MainActivity.this, 10),
                  Ui.dp(MainActivity.this, 12),
                  Ui.dp(MainActivity.this, 4),
                  Ui.dp(MainActivity.this, 12));
              card.setBackground(Ui.bg(Ui.CARD, Ui.dp(MainActivity.this, 14)));
              outer.addView(card, new LinearLayout.LayoutParams(-1, -2));
              h = new Holder();
              h.artwork = new FrameLayout(MainActivity.this);
              h.artwork.setBackground(Ui.bg(0xFF23334A, Ui.dp(MainActivity.this, 10)));
              h.artwork.setClipToOutline(true);
              card.addView(
                  h.artwork,
                  new LinearLayout.LayoutParams(
                      Ui.dp(MainActivity.this, 100), Ui.dp(MainActivity.this, 66)));
              h.symbol = Ui.text(MainActivity.this, "▶", 26, Ui.GREEN);
              h.symbol.setGravity(Gravity.CENTER);
              h.artwork.addView(h.symbol, new FrameLayout.LayoutParams(-1, -1));
              h.image = new ImageView(MainActivity.this);
              h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
              h.image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
              h.artwork.addView(h.image, new FrameLayout.LayoutParams(-1, -1));
              LinearLayout text = new LinearLayout(MainActivity.this);
              text.setOrientation(LinearLayout.VERTICAL);
              LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, -2, 1);
              textLp.setMargins(Ui.dp(MainActivity.this, 8), 0, 0, 0);
              card.addView(text, textLp);
              h.title = Ui.text(MainActivity.this, "", 15, Color.WHITE);
              h.title.setTypeface(null, android.graphics.Typeface.BOLD);
              h.title.setMaxLines(2);
              h.title.setEllipsize(TextUtils.TruncateAt.END);
              text.addView(h.title);
              h.detail = Ui.text(MainActivity.this, "", 11, Ui.MUTED);
              h.detail.setMaxLines(3);
              text.addView(h.detail);
              h.progress =
                  new ProgressBar(
                      MainActivity.this, null, android.R.attr.progressBarStyleHorizontal);
              h.progress.setMax(1000);
              h.progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Ui.GREEN));
              text.addView(
                  h.progress, new LinearLayout.LayoutParams(-1, Ui.dp(MainActivity.this, 3)));
              h.more = Ui.button(MainActivity.this, "⋮", () -> {});
              card.addView(
                  h.more,
                  new LinearLayout.LayoutParams(
                      Ui.dp(MainActivity.this, 44), Ui.dp(MainActivity.this, 48)));
              outer.setTag(h);
            } else {
              outer = (LinearLayout) recycled;
              h = (Holder) outer.getTag();
            }
            Row row = rows.get(position);
            h.title.setText(row.title);
            h.detail.setText(row.detail);
            h.symbol.setText(row.symbol);
            h.more.setVisibility(row.more == null ? View.GONE : View.VISIBLE);
            h.more.setContentDescription("Options for " + row.title);
            h.more.setOnClickListener(
                v -> {
                  if (row.more != null) row.more.run();
                });
            h.progress.setVisibility(View.GONE);
            h.image.setImageDrawable(null);
            h.image.setTag(null);
            if (row.video != null) {
              long resume = prefs.getLong("pos:" + row.video.uri, 0);
              if (row.video.duration > 0 && resume > 0 && prefs.getBoolean("history", true)) {
                h.progress.setVisibility(View.VISIBLE);
                h.progress.setProgress((int) Math.min(1000, resume * 1000 / row.video.duration));
              }
              loadThumbnail(row.video, h.image);
            }
            outer.setOnClickListener(v -> row.tap.run());
            outer.setOnLongClickListener(
                v -> {
                  if (row.more != null) {
                    row.more.run();
                    return true;
                  }
                  return false;
                });
            return outer;
          }
        };
    list.setAdapter(adapter);
  }

  void add(LinearLayout row, String s, Runnable r) {
    Button b = Ui.button(this, s, r);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 46));
    lp.setMargins(4, 4, 4, 4);
    row.addView(b, lp);
  }

  String permission() {
    return Build.VERSION.SDK_INT >= 33
        ? Manifest.permission.READ_MEDIA_VIDEO
        : Manifest.permission.READ_EXTERNAL_STORAGE;
  }

  String[] permissions() {
    return Build.VERSION.SDK_INT >= 34
        ? new String[] {
          Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        }
        : new String[] {permission()};
  }

  boolean canBrowse() {
    return checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED
        || (Build.VERSION.SDK_INT >= 34
            && checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                == PackageManager.PERMISSION_GRANTED);
  }

  void scan() {
    if (!alive) return;
    final int generation = ++scanGeneration;
    if (scanSignal != null) scanSignal.cancel();
    scanSignal = null;
    if (!canBrowse()) {
      scanning = false;
      videos.clear();
      thumbnails.evictAll();
      render();
      return;
    }
    final CancellationSignal signal = new CancellationSignal();
    scanSignal = signal;
    scanning = true;
    final Set<String> excluded =
        new HashSet<>(prefs.getStringSet("excluded_folders", Collections.emptySet()));
    final int minimum = prefs.getInt("minimum_seconds", 0);
    final boolean hidden = prefs.getBoolean("show_hidden", false);
    summary.setText(R.string.updating_library);
    loader.execute(
        () -> {
          if (!alive || generation != scanGeneration) return;
          ArrayList<Video> found = new ArrayList<>();
          Set<String> known = new HashSet<>();
          boolean success = false;
          try (Cursor c =
              getContentResolver()
                  .query(
                      MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                      new String[] {
                        "_id",
                        "_display_name",
                        "bucket_display_name",
                        "duration",
                        "_size",
                        "date_added",
                        Build.VERSION.SDK_INT >= 29 ? "relative_path" : "_data",
                        Build.VERSION.SDK_INT >= 29 ? "volume_name" : "bucket_id"
                      },
                      null,
                      null,
                      "date_added DESC",
                      signal)) {
            if (c == null) throw new IllegalStateException("Media provider returned no cursor");
            while (c.moveToNext()) {
              if (!alive || generation != scanGeneration) return;
              signal.throwIfCanceled();
              String name = c.getString(1) == null ? "Untitled video" : c.getString(1);
              String folder =
                  c.getString(6) == null
                      ? (c.getString(2) == null ? "Videos" : c.getString(2))
                      : c.getString(6);
              Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
              if (Build.VERSION.SDK_INT >= 29) {
                String volume = c.getString(7);
                if (volume != null && !volume.isEmpty()) {
                  if (!MediaStore.VOLUME_EXTERNAL_PRIMARY.equals(volume)
                      && !MediaStore.VOLUME_EXTERNAL.equals(volume))
                    folder = "[" + volume + "] " + folder;
                }
              } else if (c.getString(6) != null) {
                String parent = new java.io.File(c.getString(6)).getParent();
                if (parent != null) folder = parent;
              }
              if (folder.isEmpty()) folder = "Device root/";
              known.add(folder);
              if (!SettingsRules.visible(name, folder, c.getLong(3), minimum, hidden, excluded))
                continue;
              found.add(
                  new Video(
                      ContentUris.withAppendedId(collection, c.getLong(0)).toString(),
                      name,
                      folder,
                      c.getLong(3),
                      c.getLong(4),
                      c.getLong(5)));
            }
            signal.throwIfCanceled();
            success = true;
          } catch (OperationCanceledException ignored) {
          } catch (Exception error) {
            runOnUiThread(
                () -> {
                  if (alive && generation == scanGeneration)
                    toast("Couldn't refresh videos. Check video access or open a file with ＋.");
                });
          }
          final boolean completed = success;
          runOnUiThread(
              () -> {
                if (!alive || generation != scanGeneration) return;
                scanning = false;
                scanSignal = null;
                if (completed) {
                  prefs.edit().putStringSet("known_folders", known).apply();
                  videos.clear();
                  videos.addAll(found);
                }
                render();
              });
        });
  }

  @Override
  public void onRequestPermissionsResult(int r, String[] p, int[] g) {
    super.onRequestPermissionsResult(r, p, g);
    scan();
  }

  @Override
  protected void onResume() {
    super.onResume();
    String signature = AppSettings.themeSignature(prefs);
    Ui.configure(this);
    if (!signature.equals(appliedTheme)) {
      String query = search.getText().toString();
      appliedTheme = signature;
      build();
      search.setText(query);
    }
    sort = prefs.getString("sort", "Newest");
    if (!prefs.getBoolean("thumbnails", true)) thumbnails.evictAll();
    if (list != null) scan();
  }

  Set<String> set(String key) {
    return new HashSet<>(prefs.getStringSet(key, Collections.emptySet()));
  }

  void loadThumbnail(Video video, ImageView image) {
    if (!prefs.getBoolean("thumbnails", true)) return;
    String key = video.uri + ":" + video.size;
    image.setTag(key);
    Bitmap cached = thumbnails.get(key);
    if (cached != null) {
      image.setImageBitmap(cached);
      return;
    }
    thumbnailLoader.execute(
        () -> {
          Bitmap bitmap = null;
          try {
            if (Build.VERSION.SDK_INT >= 29)
              bitmap =
                  getContentResolver()
                      .loadThumbnail(Uri.parse(video.uri), new Size(200, 132), null);
            else
              bitmap =
                  MediaStore.Video.Thumbnails.getThumbnail(
                      getContentResolver(),
                      ContentUris.parseId(Uri.parse(video.uri)),
                      MediaStore.Video.Thumbnails.MINI_KIND,
                      null);
            if (bitmap != null) {
              Bitmap scaled = android.media.ThumbnailUtils.extractThumbnail(bitmap, 200, 132);
              bitmap = scaled;
              thumbnails.put(key, bitmap);
            }
          } catch (Exception ignored) {
          }
          Bitmap result = bitmap;
          if (result != null)
            runOnUiThread(
                () -> {
                  if (alive && key.equals(image.getTag())) image.setImageBitmap(result);
                });
        });
  }

  void render() {
    if (list == null) return;
    rows.clear();
    for (String key : tabButtons.keySet()) {
      Button button = tabButtons.get(key);
      boolean active = key.equals(tab);
      button.setTextColor(active ? Ui.BG : Ui.MUTED);
      button.setBackground(Ui.bg(active ? Ui.GREEN : Ui.CARD, Ui.dp(this, 12)));
      button.setSelected(active);
    }
    String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
    summary.setText(
        String.format(
            Locale.getDefault(),
            "%d videos • %s%s",
            videos.size(),
            tab,
            folder.isEmpty() ? "" : " / " + folder));
    if (tab.equals("Folders") && folder.isEmpty()) {
      TreeMap<String, Integer> counts = new TreeMap<>();
      for (Video v : videos)
        if (v.folder.toLowerCase(Locale.ROOT).contains(query))
          counts.put(v.folder, counts.getOrDefault(v.folder, 0) + 1);
      for (String f : counts.keySet())
        rows.add(
            new Row(
                f,
                counts.get(f) + " videos",
                "▣",
                null,
                () -> {
                  folder = f;
                  render();
                },
                null));
    } else if (tab.equals("Playlists") && folder.isEmpty()) {
      rows.add(
          new Row(
              "Create a playlist",
              "Keep your favourites together",
              "+",
              null,
              () -> newPlaylist(),
              null));
      for (String p : new TreeSet<>(set("playlists")))
        if (p.toLowerCase(Locale.ROOT).contains(query))
          rows.add(
              new Row(
                  p,
                  set("playlist:" + p).size() + " saved videos",
                  "☷",
                  null,
                  () -> {
                    folder = p;
                    render();
                  },
                  () -> playlistOptions(p)));
    } else {
      ArrayList<Video> shown = new ArrayList<>();
      Set<String> playlist = set("playlist:" + folder);
      for (Video v : videos) {
        if (!v.name.toLowerCase(Locale.ROOT).contains(query)
            && !v.folder.toLowerCase(Locale.ROOT).contains(query)) continue;
        if (tab.equals("Folders") && !v.folder.equals(folder)) continue;
        if (tab.equals("Favourites") && !prefs.getBoolean("fav:" + v.uri, false)) continue;
        if (tab.equals("Recent")
            && (!prefs.getBoolean("history", true) || prefs.getLong("seen:" + v.uri, 0) == 0))
          continue;
        if (tab.equals("Playlists") && !playlist.contains(v.uri)) continue;
        shown.add(v);
      }
      if (tab.equals("Recent"))
        shown.sort(
            (a, b) ->
                Long.compare(prefs.getLong("seen:" + b.uri, 0), prefs.getLong("seen:" + a.uri, 0)));
      else if (sort.equals("Name")) shown.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
      else if (sort.equals("Duration")) shown.sort((a, b) -> Long.compare(b.duration, a.duration));
      else if (sort.equals("Size")) shown.sort((a, b) -> Long.compare(b.size, a.size));
      if (tab.equals("All videos") && query.isEmpty() && prefs.getBoolean("history", true)) {
        Video recent = null;
        long seen = 0;
        for (Video v : videos) {
          long position = prefs.getLong("pos:" + v.uri, 0),
              time = prefs.getLong("seen:" + v.uri, 0);
          if (position > 0 && PlaybackMath.resume(position, v.duration) > 0 && time > seen) {
            recent = v;
            seen = time;
          }
        }
        if (recent != null) {
          Video v = recent;
          rows.add(
              new Row(
                  "Continue watching",
                  v.name + " • " + PlaybackMath.time(prefs.getLong("pos:" + v.uri, 0)),
                  "▶",
                  v,
                  () -> play(new ArrayList<>(Collections.singletonList(v)), 0),
                  () -> videoOptions(v)));
        }
      }
      for (int j = 0; j < shown.size(); j++) {
        Video v = shown.get(j);
        int selected = j;
        String detail =
            PlaybackMath.time(v.duration)
                + " • "
                + String.format(Locale.getDefault(), "%.1f MB", v.size / 1048576d)
                + "\n"
                + v.folder;
        if (prefs.getBoolean("fav:" + v.uri, false)) detail += "  ★";
        rows.add(
            new Row(v.name, detail, "▶", v, () -> play(shown, selected), () -> videoOptions(v)));
      }
    }
    if (rows.isEmpty()) {
      String message =
          !query.isEmpty()
              ? "Try another name or folder"
              : canBrowse()
                  ? "Open a video or refresh your library"
                  : "Allow access to browse videos on this device";
      rows.add(
          new Row(
              !query.isEmpty() ? "No matches" : "Nothing here yet",
              message,
              "▶",
              null,
              () -> pick(),
              null));
      if (!canBrowse())
        rows.add(
            new Row(
                "Allow video access",
                "You can also choose individual files",
                "+",
                null,
                () -> requestPermissions(permissions(), 10),
                null));
    }
    adapter.notifyDataSetChanged();
  }

  void playlistOptions(String name) {
    new AlertDialog.Builder(this)
        .setTitle(name)
        .setItems(
            new String[] {"Rename playlist", "Delete playlist"},
            (d, w) -> {
              if (w == 0) {
                EditText e = new EditText(this);
                e.setText(name);
                new AlertDialog.Builder(this)
                    .setTitle("Rename playlist")
                    .setView(e)
                    .setPositiveButton(
                        "Save",
                        (a, b) -> {
                          String n = e.getText().toString().trim();
                          Set<String> playlists = set("playlists");
                          if (n.isEmpty() || n.equals(name)) return;
                          if (playlists.contains(n)) {
                            toast("That playlist already exists");
                            return;
                          }
                          Set<String> entries = set("playlist:" + name);
                          playlists.remove(name);
                          playlists.add(n);
                          prefs
                              .edit()
                              .putStringSet("playlists", playlists)
                              .putStringSet("playlist:" + n, entries)
                              .remove("playlist:" + name)
                              .apply();
                          render();
                        })
                    .setNegativeButton("Cancel", null)
                    .show();
              } else
                new AlertDialog.Builder(this)
                    .setMessage("Delete playlist? Videos stay on your phone.")
                    .setPositiveButton(
                        "Delete",
                        (a, b) -> {
                          Set<String> p = set("playlists");
                          p.remove(name);
                          prefs
                              .edit()
                              .putStringSet("playlists", p)
                              .remove("playlist:" + name)
                              .apply();
                          render();
                        })
                    .setNegativeButton("Cancel", null)
                    .show();
            })
        .show();
  }

  void play(ArrayList<Video> queue, int index) {
    if (!alive) return;
    ArrayList<String> uris = new ArrayList<>(), names = new ArrayList<>();
    for (Video v : queue) {
      uris.add(v.uri);
      names.add(v.name);
    }
    loader.execute(
        () -> {
          try {
            String token = QueueStore.write(this, new QueueCodec.Queue(uris, names, index));
            runOnUiThread(
                () -> {
                  if (alive) {
                    Intent i = new Intent(this, PlayerActivity.class);
                    i.putExtra("queue", token);
                    startActivity(i);
                  }
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (alive) toast("Couldn't open this playlist. Try opening a single video.");
                });
          }
        });
  }

  void videoOptions(Video v) {
    ArrayList<String> opts =
        new ArrayList<>(
            Arrays.asList(
                prefs.getBoolean("fav:" + v.uri, false) ? "Remove favourite" : "Add to favourites",
                "Add to playlist",
                "Share video",
                "Video details"));
    if (tab.equals("Playlists") && !folder.isEmpty()) opts.add("Remove from this playlist");
    new AlertDialog.Builder(this)
        .setTitle(v.name)
        .setItems(
            opts.toArray(new String[0]),
            (d, w) -> {
              if (w == 0) {
                prefs
                    .edit()
                    .putBoolean("fav:" + v.uri, !prefs.getBoolean("fav:" + v.uri, false))
                    .apply();
                render();
              }
              if (w == 1) {
                ArrayList<String> ps = new ArrayList<>(new TreeSet<>(set("playlists")));
                if (ps.isEmpty()) {
                  toast("Create a playlist in the Playlists tab first.");
                  return;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Add to playlist")
                    .setItems(
                        ps.toArray(new String[0]),
                        (a, b) -> {
                          Set<String> s = set("playlist:" + ps.get(b));
                          s.add(v.uri);
                          prefs.edit().putStringSet("playlist:" + ps.get(b), s).apply();
                          toast("Added to " + ps.get(b));
                        })
                    .show();
              }
              if (w == 2) {
                Intent s = new Intent(Intent.ACTION_SEND);
                s.setType("video/*");
                s.putExtra(Intent.EXTRA_STREAM, Uri.parse(v.uri));
                s.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(s, "Share video"));
              }
              if (w == 4) {
                if (tab.equals("Playlists") && !folder.isEmpty()) {
                  Set<String> entries = set("playlist:" + folder);
                  entries.remove(v.uri);
                  prefs.edit().putStringSet("playlist:" + folder, entries).apply();
                  render();
                } else toast("Open a playlist to remove a video from it.");
              }
              if (w == 3)
                new AlertDialog.Builder(this)
                    .setTitle(v.name)
                    .setMessage(
                        "Folder: "
                            + v.folder
                            + "\nLength: "
                            + PlaybackMath.time(v.duration)
                            + "\nSize: "
                            + v.size
                            + " bytes")
                    .setPositiveButton("OK", null)
                    .show();
            })
        .show();
  }

  void newPlaylist() {
    EditText e = new EditText(this);
    e.setHint("Playlist name");
    new AlertDialog.Builder(this)
        .setTitle("New playlist")
        .setView(e)
        .setPositiveButton(
            "Create",
            (d, w) -> {
              String n = e.getText().toString().trim();
              if (n.isEmpty()) {
                toast("Enter a playlist name");
                return;
              }
              Set<String> s = set("playlists");
              if (s.contains(n)) {
                toast("That playlist already exists");
                return;
              }
              s.add(n);
              prefs.edit().putStringSet("playlists", s).apply();
              render();
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void options() {
    new AlertDialog.Builder(this)
        .setTitle("Sumi Vid")
        .setItems(
            new String[] {
              "Refresh library",
              "Sort videos",
              "Settings",
              "About & gestures",
              "Open network stream",
              "Manage video access"
            },
            (d, w) -> {
              if (w == 0) scan();
              if (w == 4) stream();
              if (w == 5) requestPermissions(permissions(), 10);
              if (w == 1)
                new AlertDialog.Builder(this)
                    .setTitle("Sort by")
                    .setItems(
                        new String[] {"Newest", "Name", "Duration", "Size"},
                        (a, b) -> {
                          sort = new String[] {"Newest", "Name", "Duration", "Size"}[b];
                          prefs.edit().putString("sort", sort).apply();
                          render();
                        })
                    .show();
              if (w == 2) startActivity(new Intent(this, SettingsActivity.class));
              if (w == 3)
                new AlertDialog.Builder(this)
                    .setTitle("Sumi Vid " + BuildConfig.VERSION_NAME)
                    .setMessage(
                        "Built for Lord_Sumi.\n\n"
                            + "Double-tap the left/right side: seek 10 seconds.\n"
                            + "Swipe vertically: brightness on the left, volume on the right.\n"
                            + "Swipe horizontally: seek.\n"
                            + "Use Lock to prevent accidental touches.\n\n"
                            + "Formats depend on your device's codecs. No ads, analytics or account"
                            + " required. Local videos are not uploaded. HTTPS streaming connects"
                            + " only to your chosen server.")
                    .setPositiveButton("OK", null)
                    .show();
            })
        .show();
  }

  void pick() {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.setType("video/*");
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
    startActivityForResult(i, 100);
  }

  void stream() {
    EditText e = new EditText(this);
    e.setHint("https://example.com/video.mp4");
    e.setInputType(17);
    new AlertDialog.Builder(this)
        .setTitle("Open network stream")
        .setMessage("Enter a direct HTTPS video, HLS or DASH URL.")
        .setView(e)
        .setPositiveButton(
            "Play",
            (d, w) -> {
              String u = e.getText().toString().trim();
              if (!u.startsWith("https://")) {
                toast("Enter a valid HTTPS URL.");
                return;
              }
              open(Uri.parse(u));
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void open(Uri u) {
    Intent i = new Intent(this, PlayerActivity.class);
    i.setData(u);
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivity(i);
  }

  @Override
  protected void onActivityResult(int r, int c, Intent d) {
    super.onActivityResult(r, c, d);
    if (r == 100 && c == RESULT_OK && d != null && d.getData() != null) {
      try {
        getContentResolver()
            .takePersistableUriPermission(d.getData(), Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (SecurityException ignored) {
      }
      open(d.getData());
    }
  }

  void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onBackPressed() {
    if (!folder.isEmpty()) {
      folder = "";
      render();
    } else super.onBackPressed();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("tab", tab);
    state.putString("folder", folder);
    state.putString("query", search.getText().toString());
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onDestroy() {
    alive = false;
    ++scanGeneration;
    if (scanSignal != null) scanSignal.cancel();
    loader.shutdownNow();
    thumbnailLoader.shutdownNow();
    thumbnails.evictAll();
    super.onDestroy();
  }
}
