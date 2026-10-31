package com.sumi.vid;

import java.io.*;
import java.util.*;

/** Versioned, bounded queue format. Keeps large libraries out of Android Intent parcels. */
public final class QueueCodec {
  private static final int MAGIC = 0x53565131;
  static final int MAX_ITEMS = 50000;

  public static final class Queue {
    public final ArrayList<String> uris, names;
    public final int index;

    public Queue(List<String> uris, List<String> names, int index) {
      if (uris == null
          || names == null
          || uris.isEmpty()
          || uris.size() > MAX_ITEMS
          || uris.size() != names.size()
          || index < 0
          || index >= uris.size()) throw new IllegalArgumentException("Invalid queue");
      for (int j = 0; j < uris.size(); j++)
        if (uris.get(j) == null || uris.get(j).isEmpty() || names.get(j) == null)
          throw new IllegalArgumentException("Invalid item");
      this.uris = new ArrayList<>(uris);
      this.names = new ArrayList<>(names);
      this.index = index;
    }
  }

  public static void write(OutputStream out, Queue queue) throws IOException {
    DataOutputStream data = new DataOutputStream(out);
    data.writeInt(MAGIC);
    data.writeInt(queue.uris.size());
    data.writeInt(queue.index);
    for (int j = 0; j < queue.uris.size(); j++) {
      data.writeUTF(queue.uris.get(j));
      data.writeUTF(queue.names.get(j));
    }
    data.flush();
  }

  public static Queue read(InputStream input) throws IOException {
    DataInputStream data = new DataInputStream(input);
    if (data.readInt() != MAGIC) throw new IOException("Unknown queue format");
    int count = data.readInt(), index = data.readInt();
    if (count < 1 || count > MAX_ITEMS || index < 0 || index >= count)
      throw new IOException("Invalid queue bounds");
    ArrayList<String> uris = new ArrayList<>(), names = new ArrayList<>();
    for (int j = 0; j < count; j++) {
      uris.add(data.readUTF());
      names.add(data.readUTF());
    }
    try {
      return new Queue(uris, names, index);
    } catch (IllegalArgumentException e) {
      throw new IOException("Invalid queue item", e);
    }
  }
}
