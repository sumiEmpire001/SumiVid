package com.sumi.vid;

import static org.junit.Assert.*;

import org.junit.Test;

public class PlaybackMathTest {
  @Test
  public void seekNeverEscapesVideo() {
    assertEquals(0, PlaybackMath.seek(2000, -10000, 60000));
    assertEquals(60000, PlaybackMath.seek(59000, 10000, 60000));
    assertEquals(12000, PlaybackMath.seek(2000, 10000, 60000));
  }

  @Test
  public void completedVideoRestarts() {
    assertEquals(0, PlaybackMath.resume(59000, 60000));
    assertEquals(25000, PlaybackMath.resume(25000, 60000));
    assertEquals(0, PlaybackMath.resume(-1, 60000));
  }

  @Test
  public void shortClipsResumeBeforeTheirEnd() {
    assertEquals(200, PlaybackMath.resume(200, 1000));
    assertEquals(0, PlaybackMath.resume(950, 1000));
  }

  @Test
  public void longVideoTimestamp() {
    assertEquals("1:01:01", PlaybackMath.time(3661000));
    assertEquals("0:00", PlaybackMath.time(-1));
    assertEquals("2:05", PlaybackMath.time(125000));
  }
}
