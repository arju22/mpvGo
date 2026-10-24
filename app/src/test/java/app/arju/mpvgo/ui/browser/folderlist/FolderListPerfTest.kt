package app.arju.mpvgo.ui.browser.folderlist

import app.arju.mpvgo.domain.media.model.VideoFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderListPerfTest {
  private fun f(bucket: String, n: Int) =
    FolderWithNewCount(VideoFolder(bucketId = bucket, name = bucket, path = bucket, videoCount = 1), n)

  @Test
  fun firstEntryWinsLikeFind() {
    val map = newCountsByBucket(listOf(f("/a", 2), f("/b", 0), f("/a", 9)))
    assertEquals(2, map["/a"])
    assertEquals(0, map["/b"])
    assertEquals(null, map["/c"])
  }
}
