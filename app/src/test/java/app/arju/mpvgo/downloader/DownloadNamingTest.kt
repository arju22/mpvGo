package app.arju.mpvgo.downloader

import app.arju.mpvgo.downloader.engine.DownloadNaming
import app.arju.mpvgo.downloader.engine.FailReason
import app.arju.mpvgo.downloader.engine.RetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class DownloadNamingTest {

  @Test
  fun `generic hls names use the cleaned page title`() {
    val name = DownloadNaming.suggest(
      url = "https://cdn.site/v/master.m3u8?t=1",
      pageTitle = "Watch Jujutsu Kaisen Season 2 Episode 5 English Sub Online Free HD - AnimeSite.to",
      isHls = true,
    )
    assertEquals("Jujutsu Kaisen Season 2 Episode 5.mp4", name)
  }

  @Test
  fun `meaningful link names are kept`() {
    assertEquals(
      "concert_live.mkv",
      DownloadNaming.suggest("https://x.com/files/concert_live.mkv?sig=1", pageTitle = "Some page | X"),
    )
  }

  @Test
  fun `generic link names fall back to the title`() {
    assertEquals("My Clip.mp4", DownloadNaming.suggest("https://x.com/video.mp4", pageTitle = "My Clip - X.com"))
    assertTrue(DownloadNaming.isGeneric("index-v1-a1"))
    assertTrue(DownloadNaming.isGeneric("master_720p"))
    assertFalse(DownloadNaming.isGeneric("episode_05"))
  }

  @Test
  fun `content disposition wins for direct files`() {
    assertEquals(
      "Movie Night.mkv",
      DownloadNaming.suggest("https://x.com/dl?id=5", contentDisposition = "attachment; filename*=UTF-8''Movie%20Night.mkv"),
    )
    assertEquals("a b.mp4", DownloadNaming.fileNameFromDisposition("attachment; filename=\"a b.mp4\""))
  }

  @Test
  fun `extension from mime when url has none`() {
    assertEquals("clip.webm", DownloadNaming.suggest("https://x.com/get/clip", mimeType = "video/webm; codecs=vp9"))
    assertEquals("video.mp4", DownloadNaming.suggest("https://x.com/"))
  }

  @Test
  fun `illegal characters are removed`() {
    assertEquals("a b c", DownloadNaming.sanitize("a/b:c?"))
  }

  @Test
  fun `unique names get a counter`() {
    val taken = setOf("a.mp4", "a (1).mp4")
    assertEquals("a (2).mp4", DownloadNaming.unique("a.mp4") { it in taken })
    assertEquals("b.mp4", DownloadNaming.unique("b.mp4") { it in taken })
    assertEquals("a.ts", DownloadNaming.withExtension("a.mp4", "ts"))
  }

  @Test
  fun `hls detection`() {
    assertTrue(DownloadNaming.isHlsUrl("https://x/a.M3U8?x=1"))
    assertTrue(DownloadNaming.isHlsUrl("https://x/a", "application/vnd.apple.mpegurl"))
    assertFalse(DownloadNaming.isHlsUrl("https://x/a.mp4"))
  }

  @Test
  fun `failure reasons and retry policy`() {
    assertEquals(FailReason.LinkExpired, FailReason.fromHttp(403))
    assertEquals(FailReason.NotFound, FailReason.fromHttp(404))
    assertEquals(FailReason.ServerError, FailReason.fromHttp(503))
    assertEquals(FailReason.NoConnection, FailReason.fromThrowable(UnknownHostException()))
    assertEquals(FailReason.StorageFull, FailReason.fromThrowable(java.io.IOException("write failed: ENOSPC")))
    assertTrue(RetryPolicy.shouldRetry(FailReason.NoConnection, 9))
    assertFalse(RetryPolicy.shouldRetry(FailReason.NoConnection, 10))
    assertFalse(RetryPolicy.shouldRetry(FailReason.LinkExpired, 1))
    assertEquals(1000L, RetryPolicy.delayMs(1))
    assertEquals(30_000L, RetryPolicy.delayMs(10))
  }
}
