package app.marlboroadvance.mpvex.ui.browser.playlist

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistCountsTest {
  @Test
  fun m3uCountsAllItems() {
    assertEquals(3, PlaylistCounts.countAvailable(true, listOf("http://a", "http://b", "http://c"), emptySet()))
  }

  @Test
  fun localCountsOnlyAvailablePaths() {
    val available = setOf("/v/a.mp4", "/v/c.mkv")
    assertEquals(2, PlaylistCounts.countAvailable(false, listOf("/v/a.mp4", "/v/b.mp4", "/v/c.mkv"), available))
  }

  @Test
  fun duplicatesAndEmpty() {
    assertEquals(2, PlaylistCounts.countAvailable(false, listOf("/v/a.mp4", "/v/a.mp4"), setOf("/v/a.mp4")))
    assertEquals(0, PlaylistCounts.countAvailable(false, emptyList(), setOf("/v/a.mp4")))
  }
}
