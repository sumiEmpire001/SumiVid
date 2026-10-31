package com.sumi.vid;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import org.junit.Test;

public class QueueCodecTest {
  @Test
  public void preservesSelectionAndUnicode() throws Exception {
    QueueCodec.Queue q =
        new QueueCodec.Queue(
            Arrays.asList("content://media/video/1", "content://media/video/2"),
            Arrays.asList("Movie 🎬", "Ọjọ́ tuntun"),
            1);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    QueueCodec.write(out, q);
    QueueCodec.Queue decoded = QueueCodec.read(new ByteArrayInputStream(out.toByteArray()));
    assertEquals(q.uris, decoded.uris);
    assertEquals(q.names, decoded.names);
    assertEquals(1, decoded.index);
  }

  @Test
  public void largeLibraryRoundTrip() throws Exception {
    ArrayList<String> uris = new ArrayList<>(), names = new ArrayList<>();
    for (int j = 0; j < 10000; j++) {
      uris.add("content://media/external/video/media/" + j);
      names.add("Video " + j);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    QueueCodec.write(out, new QueueCodec.Queue(uris, names, 9999));
    assertEquals(10000, QueueCodec.read(new ByteArrayInputStream(out.toByteArray())).uris.size());
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMismatchedMetadata() {
    new QueueCodec.Queue(Arrays.asList("x"), Collections.emptyList(), 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsInvalidSelection() {
    new QueueCodec.Queue(Arrays.asList("x"), Arrays.asList("title"), 1);
  }

  @Test(expected = IOException.class)
  public void rejectsTruncatedQueue() throws Exception {
    QueueCodec.read(new ByteArrayInputStream(new byte[] {1, 2, 3}));
  }

  @Test(expected = IOException.class)
  public void rejectsUntrustedCount() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    DataOutputStream data = new DataOutputStream(out);
    data.writeInt(0x53565131);
    data.writeInt(Integer.MAX_VALUE);
    data.writeInt(0);
    QueueCodec.read(new ByteArrayInputStream(out.toByteArray()));
  }
}
