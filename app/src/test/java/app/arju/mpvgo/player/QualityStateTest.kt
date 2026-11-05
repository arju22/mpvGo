package app.arju.mpvgo.player

import app.arju.mpvgo.player.QualityState.Companion.buildQualitiesFromTracks
import app.arju.mpvgo.player.QualityState.Companion.formatBitrate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityStateTest {

  @Test
  fun testFormatBitrate() {
    assertEquals("", formatBitrate(0L))
    assertEquals("500 Kbps", formatBitrate(500_000L))
    assertEquals("1.2 Mbps", formatBitrate(1_200_000L))
    assertEquals("5.0 Mbps", formatBitrate(5_000_000L))
  }

  @Test
  fun testBuildQualitiesFromTracks() {
    val tracks = listOf(
      TrackNode(id = 1, type = "video", title = "1080p", demuxH = 1080, demuxW = 1920, demuxBitrate = 5_000_000),
      TrackNode(id = 2, type = "video", title = "720p", demuxH = 720, demuxW = 1280, demuxBitrate = 2_800_000),
      TrackNode(id = 3, type = "video", title = "480p", demuxH = 480, demuxW = 854, demuxBitrate = 1_200_000),
      TrackNode(id = 4, type = "video", title = "360p", demuxH = 360, demuxW = 640, demuxBitrate = 700_000),
      TrackNode(id = 5, type = "audio", title = "English audio"),
    )

    val qualities = buildQualitiesFromTracks(tracks)
    // 1 auto + 4 variants = 5
    assertEquals(5, qualities.size)
    assertTrue(qualities[0].isAuto)

    // Sorted descending by height
    assertEquals(1080, qualities[1].height)
    assertEquals("1080p", qualities[1].label)
    assertTrue(qualities[1].isHd)
    assertFalse(qualities[1].isDataSaver)
    assertEquals("5.0 Mbps", qualities[1].bitrateText)

    assertEquals(720, qualities[2].height)
    assertTrue(qualities[2].isHd)

    assertEquals(480, qualities[3].height)
    assertFalse(qualities[3].isHd)
    assertFalse(qualities[3].isDataSaver)

    assertEquals(360, qualities[4].height)
    assertFalse(qualities[4].isHd)
    assertTrue(qualities[4].isDataSaver)
    assertEquals("700 Kbps", qualities[4].bitrateText)
  }

  @Test
  fun testQualityStateDisplay() {
    val state = QualityState(
      isOnlineStream = true,
      activeHeight = 720,
      qualities = listOf(
        StreamQuality(id = -1, label = "Auto", isAuto = true),
        StreamQuality(id = 1, label = "720p", height = 720, isHd = true),
        StreamQuality(id = 2, label = "480p", height = 480),
      ),
    )

    assertTrue(state.hasMultipleQualities)
    assertEquals("Auto · 720p", state.chipText)
    assertTrue(state.isHd)

    val manualState = state.copy(selectedQuality = StreamQuality(id = 2, label = "480p", height = 480))
    assertEquals("480p", manualState.chipText)
    assertFalse(manualState.isHd)

    // Local stream should never show multiple qualities
    val localState = state.copy(isOnlineStream = false)
    assertFalse(localState.hasMultipleQualities)
  }
}
