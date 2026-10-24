package app.marlboroadvance.mpvex.utils.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MissingMediaScanTest {
  @Test
  fun onlyUnindexedFilesAreScanned() {
    val onDisk = listOf("/v/a.mp4", "/v/b.mkv", "/v/c.mp4")
    assertEquals(listOf("/v/b.mkv"), MissingMediaScan.missing(onDisk, setOf("/v/a.mp4", "/v/c.mp4", "/v/old.mp4")))
  }

  @Test
  fun nothingMissing() {
    assertEquals(emptyList<String>(), MissingMediaScan.missing(listOf("/v/a.mp4"), setOf("/v/a.mp4")))
    assertEquals(listOf("/v/a.mp4"), MissingMediaScan.missing(listOf("/v/a.mp4"), emptySet()))
  }
}
