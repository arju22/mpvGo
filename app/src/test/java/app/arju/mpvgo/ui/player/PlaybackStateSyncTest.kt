package app.arju.mpvgo.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateSyncTest {
  @Test
  fun steadyPlaybackNeedsNoUpdate() {
    assertFalse(PlaybackStateSync.needsUpdate(10_000, 0, 1f, true, 40_200, 30_000))
  }

  @Test
  fun seekForwardNeedsUpdate() {
    assertTrue(PlaybackStateSync.needsUpdate(10_000, 0, 1f, true, 90_000, 5_000))
  }

  @Test
  fun speedChangeIsDetectedOnceDriftExceedsThreshold() {
    assertFalse(PlaybackStateSync.needsUpdate(0, 0, 1f, true, 2_000, 1_000))
    assertTrue(PlaybackStateSync.needsUpdate(0, 0, 1f, true, 4_000, 2_000))
  }

  @Test
  fun pausedPositionDoesNotExtrapolate() {
    assertFalse(PlaybackStateSync.needsUpdate(5_000, 0, 1f, false, 5_000, 60_000))
    assertTrue(PlaybackStateSync.needsUpdate(5_000, 0, 1f, false, 30_000, 60_000))
  }
}
