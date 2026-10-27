package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.downloader.LiveProgress
import app.arju.mpvgo.downloader.data.DownloadEntity
import app.arju.mpvgo.downloader.data.DownloadStatus
import app.arju.mpvgo.downloader.engine.HlsPlaylist
import app.arju.mpvgo.downloader.ui.toNewDownload
import app.arju.mpvgo.player.MediaRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserDownloadsTest {
  private fun media(
    url: String,
    kind: MediaKind = MediaClassifier.classify(url) ?: MediaKind.FILE,
    seq: Long = 1,
    audioOnly: Boolean = false,
    variants: List<HlsPlaylist.Variant> = emptyList(),
  ) = DetectedMedia(url, kind, null, "https://site.test/", "https://site.test/", "", -1, audioOnly, variants, seq)

  private fun entity(
    url: String,
    status: DownloadStatus = DownloadStatus.Completed,
    page: String? = "https://site.test/watch",
    private: Boolean = false,
  ) = DownloadEntity(url = url, fileName = "v.mp4", folder = "", status = status, sourcePageUrl = page, isPrivate = private)

  @Test
  fun `DRM, YouTube and page-made blobs can't be downloaded`() {
    assertEquals(BrowserDownloads.Blocked.DRM, BrowserDownloads.blocked("https://site.test/", "https://cdn.test/v.mp4", drm = true))
    assertEquals(BrowserDownloads.Blocked.SITE, BrowserDownloads.blocked("https://m.youtube.com/watch?v=x", "https://cdn.test/v.mp4", false))
    assertEquals(BrowserDownloads.Blocked.SITE, BrowserDownloads.blocked("https://site.test/", "https://r1.googlevideo.com/videoplayback", false))
    assertEquals(BrowserDownloads.Blocked.LOCAL, BrowserDownloads.blocked("https://site.test/", "blob:https://site.test/123", false))
    assertEquals(BrowserDownloads.Blocked.LOCAL, BrowserDownloads.blocked("https://site.test/", "data:video/mp4;base64,AA", false))
    assertNull(BrowserDownloads.blocked("https://site.test/", "https://cdn.test/v.mp4", false))
  }

  @Test
  fun `private drafts keep no source page`() {
    val request = MediaRequest("https://cdn.test/v.mp4", title = "T", referer = "https://site.test/", cookies = "a=1", pageUrl = "https://site.test/p", isPrivate = true)
    val draft = BrowserDownloads.draft(request, "video/mp4")
    assertNull(draft.pageUrl)
    assertTrue(draft.isPrivate)
    assertEquals("a=1", draft.cookies)
    assertEquals("video/mp4", draft.mimeType)
    assertEquals("https://site.test/p", BrowserDownloads.draft(request.copy(isPrivate = false)).pageUrl)
  }

  @Test
  fun `already downloaded matches completed items by link, ignoring fragments and range params`() {
    val list = listOf(
      entity("https://cdn.test/a.mp4", status = DownloadStatus.Failed),
      entity("https://cdn.test/b.mp4?token=1&range=0-100"),
    )
    assertNull(BrowserDownloads.findCompleted(list, "https://cdn.test/a.mp4"))
    assertEquals("https://cdn.test/b.mp4?token=1&range=0-100", BrowserDownloads.findCompleted(list, "http://cdn.test/b.mp4?token=1#t=5")?.url)
    assertNull(BrowserDownloads.findCompleted(list, "https://cdn.test/b.mp4?token=2"))
  }

  @Test
  fun `stream info names the type and best quality`() {
    val hls = media("https://cdn.test/master.m3u8", variants = listOf(HlsPlaylist.Variant("a", 1, 1280, 720, null), HlsPlaylist.Variant("b", 2, 1920, 1080, null)))
    assertEquals("HLS · up to 1080p", BrowserDownloads.streamInfo(hls))
    assertEquals("MP4", BrowserDownloads.streamInfo(media("https://cdn.test/v.mp4?x=1")))
    assertEquals("MP3 · audio", BrowserDownloads.streamInfo(media("https://cdn.test/s.mp3", audioOnly = true)))
    assertEquals("Video", BrowserDownloads.streamInfo(media("https://cdn.test/play?mime=video", kind = MediaKind.FILE)))
  }

  @Test
  fun `refresh picks the same file name, else the newest stream of the same kind`() {
    val found = listOf(
      media("https://cdn2.test/new/v.mp4?sig=2", seq = 1),
      media("https://cdn2.test/other.mp4", seq = 3),
      media("https://cdn2.test/master.m3u8", seq = 4),
      media("https://cdn2.test/track.mp3", seq = 5, audioOnly = true),
    )
    assertEquals("https://cdn2.test/new/v.mp4?sig=2", BrowserDownloads.pickRefresh(found, "https://cdn.test/old/v.mp4?sig=1", isHls = false)?.url)
    assertEquals("https://cdn2.test/other.mp4", BrowserDownloads.pickRefresh(found, "https://cdn.test/x.mp4", isHls = false)?.url)
    assertEquals("https://cdn2.test/master.m3u8", BrowserDownloads.pickRefresh(found, "https://cdn.test/index.m3u8", isHls = true)?.url)
    assertNull(BrowserDownloads.pickRefresh(found.take(2), "https://cdn.test/index.m3u8", isHls = true))
  }

  @Test
  fun `progress averages known fractions`() {
    assertNull(BrowserDownloads.progress(emptyList()))
    assertEquals(-1f, BrowserDownloads.progress(listOf(LiveProgress(10, -1, 0))))
    val p = BrowserDownloads.progress(listOf(LiveProgress(50, 100, 0), LiveProgress(0, -1, 0, segmentsDone = 1, segmentsTotal = 4)))!!
    assertEquals(0.375f, p, 0.0001f)
  }

  @Test
  fun `non-video files keep the site's name until the link changes`() {
    val draft = app.arju.mpvgo.downloader.ui.DownloadDraft(url = "https://site.test/get?id=1", fileName = "report.zip")
    assertEquals("report.zip", draft.suggestName())
    assertEquals("report.zip", draft.toNewDownload().fileName)
    assertEquals("clip.mp4", draft.suggestName("https://cdn.test/clip.mp4"))
  }

  @Test
  fun `only non-private downloads with a source page can be refreshed`() {
    assertTrue(LinkRefresh.canRefresh(entity("https://cdn.test/a.mp4", DownloadStatus.Failed)))
    assertFalse(LinkRefresh.canRefresh(entity("https://cdn.test/a.mp4", DownloadStatus.Failed, private = true)))
    assertFalse(LinkRefresh.canRefresh(entity("https://cdn.test/a.mp4", DownloadStatus.Failed, page = null)))
  }
}
