package app.arju.mpvgo.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSaveGateTest {
  @Test
  fun `saves when current file is loaded and mpv reports position and duration`() {
    assertTrue(PlaybackSaveGate.canSave("b.mp4_1", "b.mp4_1", 12, 58))
    assertTrue(PlaybackSaveGate.canSave("b.mp4_1", "b.mp4_1", 0, 58))
  }

  @Test
  fun `skips while new file is still opening`() {
    // Previous file reached file-loaded, the new one has not.
    assertFalse(PlaybackSaveGate.canSave("stream.m3u8", "a.mp4_1", 40, 58))
    assertFalse(PlaybackSaveGate.canSave("stream.m3u8", "", 40, 58))
  }

  @Test
  fun `skips when mpv has no position or duration`() {
    assertFalse(PlaybackSaveGate.canSave("a", "a", null, 58))
    assertFalse(PlaybackSaveGate.canSave("a", "a", 10, null))
    assertFalse(PlaybackSaveGate.canSave("a", "a", 10, 0))
  }

  @Test
  fun `skips blank identifier`() {
    assertFalse(PlaybackSaveGate.canSave("", "", 10, 58))
  }
}
