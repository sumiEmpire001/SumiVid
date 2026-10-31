package com.sumi.vid;

import static org.junit.Assert.*;

import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.*;
import android.net.Uri;
import android.os.Looper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.*;
import org.robolectric.shadows.ShadowContentResolver;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@LooperMode(LooperMode.Mode.PAUSED)
@androidx.media3.common.util.UnstableApi
public class LibraryScanRegressionTest {
  MediaProvider provider;

  public static class ScanActivity extends MainActivity {
    boolean browse;

    @Override
    boolean canBrowse() {
      return browse;
    }

    @Override
    void loadThumbnail(Video v, android.widget.ImageView image) {}
  }

  @Before
  public void prepare() {
    AppSettings.prefs(RuntimeEnvironment.getApplication()).edit().clear().commit();
    provider = new MediaProvider();
    ProviderInfo info = new ProviderInfo();
    info.authority = "media";
    provider.attachInfo(RuntimeEnvironment.getApplication(), info);
    ShadowContentResolver.registerProviderInternal("media", provider);
  }

  static class MediaProvider extends ContentProvider {
    volatile boolean block;
    final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);

    public boolean onCreate() {
      return true;
    }

    public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sort) {
      if (block) {
        block = false;
        entered.countDown();
        try {
          release.await(4, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
      MatrixCursor cursor = new MatrixCursor(columns);
      for (int i = 0; i < 2; i++) {
        Object[] row = new Object[columns.length];
        for (int j = 0; j < columns.length; j++)
          switch (columns[j]) {
            case "_id":
              row[j] = i + 1L;
              break;
            case "_display_name":
              row[j] = i == 0 ? "short.mp4" : "long.mp4";
              break;
            case "duration":
              row[j] = i == 0 ? 5000L : 45000L;
              break;
            case "_size":
              row[j] = 1000L;
              break;
            case "date_added":
              row[j] = 100L;
              break;
            case "relative_path":
              row[j] = "Movies/";
              break;
            case "volume_name":
              row[j] = i == 0 ? "external_primary" : "1234-5678";
              break;
            default:
              row[j] = "Movies";
          }
        cursor.addRow(row);
      }
      return cursor;
    }

    public String getType(Uri uri) {
      return "video/mp4";
    }

    public Uri insert(Uri u, ContentValues v) {
      throw new UnsupportedOperationException();
    }

    public int delete(Uri u, String s, String[] a) {
      return 0;
    }

    public int update(Uri u, ContentValues v, String s, String[] a) {
      return 0;
    }
  }

  void idle(ScanActivity a) throws Exception {
    a.loader.submit(() -> {}).get(6, TimeUnit.SECONDS);
    Shadows.shadowOf(Looper.getMainLooper()).idle();
  }

  @Test
  public void filtersChangedDuringScanUseNewestRequest() throws Exception {
    try (ActivityController<ScanActivity> c =
        Robolectric.buildActivity(ScanActivity.class).setup()) {
      ScanActivity a = c.get();
      a.browse = true;
      provider.block = true;
      a.scan();
      assertTrue(provider.entered.await(3, TimeUnit.SECONDS));
      a.prefs.edit().putInt("minimum_seconds", 30).commit();
      a.scan();
      provider.release.countDown();
      idle(a);
      assertEquals(1, a.videos.size());
      assertEquals("long.mp4", a.videos.get(0).name);
    }
  }

  @Test
  public void identicalFoldersOnDifferentVolumesStaySeparate() throws Exception {
    try (ActivityController<ScanActivity> c =
        Robolectric.buildActivity(ScanActivity.class).setup()) {
      ScanActivity a = c.get();
      a.browse = true;
      a.scan();
      idle(a);
      assertEquals(2, a.videos.size());
      assertNotEquals(a.videos.get(0).folder, a.videos.get(1).folder);
      assertEquals("content://media/external/video/media/2", a.videos.get(1).uri);
    }
  }

  @Test
  public void upgradeKeepsExistingFavouritesAndPlaylistMembership() throws Exception {
    try (ActivityController<ScanActivity> c =
        Robolectric.buildActivity(ScanActivity.class).setup()) {
      ScanActivity a = c.get();
      String previousUri = "content://media/external/video/media/2";
      a.prefs
          .edit()
          .putBoolean("fav:" + previousUri, true)
          .putLong("pos:" + previousUri, 12000)
          .putStringSet("playlists", Collections.singleton("Films"))
          .putStringSet("playlist:Films", Collections.singleton(previousUri))
          .commit();
      a.browse = true;
      a.scan();
      idle(a);
      String uri = a.videos.get(1).uri;
      assertTrue(a.prefs.getBoolean("fav:" + uri, false));
      assertEquals(12000, a.prefs.getLong("pos:" + uri, 0));
      assertTrue(a.set("playlist:Films").contains(uri));
    }
  }

  @Test
  public void revokingAccessDiscardsInFlightResults() throws Exception {
    try (ActivityController<ScanActivity> c =
        Robolectric.buildActivity(ScanActivity.class).setup()) {
      ScanActivity a = c.get();
      a.browse = true;
      provider.block = true;
      a.scan();
      assertTrue(provider.entered.await(3, TimeUnit.SECONDS));
      a.browse = false;
      a.scan();
      provider.release.countDown();
      idle(a);
      assertTrue(a.videos.isEmpty());
    }
  }
}
