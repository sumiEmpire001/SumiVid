package com.sumi.vid;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.util.UUID;

final class QueueStore {
  static File directory(Context c) throws IOException {
    File d = new File(c.getCacheDir(), "queues");
    if (!d.isDirectory() && !d.mkdirs()) throw new IOException("Cannot create queue cache");
    return d;
  }

  static String write(Context c, QueueCodec.Queue queue) throws IOException {
    File directory = directory(c);
    File[] stale = directory.listFiles();
    if (stale != null)
      for (File f : stale)
        if (f.lastModified() < System.currentTimeMillis() - 7 * 86400000L) f.delete();
    String token = UUID.randomUUID().toString();
    AtomicFile file = new AtomicFile(new File(directory, token));
    FileOutputStream out = null;
    try {
      out = file.startWrite();
      QueueCodec.write(out, queue);
      file.finishWrite(out);
    } catch (IOException e) {
      if (out != null) file.failWrite(out);
      throw e;
    }
    return token;
  }

  static QueueCodec.Queue read(Context c, String token) throws IOException {
    if (token == null
        || !token.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
      throw new IOException("Invalid queue token");
    File file = new File(directory(c), token);
    if (file.length() > 16 * 1024 * 1024) throw new IOException("Queue too large");
    try (InputStream in = new AtomicFile(file).openRead()) {
      return QueueCodec.read(in);
    }
  }
}
