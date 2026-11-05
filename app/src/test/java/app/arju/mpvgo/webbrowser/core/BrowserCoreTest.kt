package app.arju.mpvgo.webbrowser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCoreTest {
  @Test
  fun `classifies manifests and files`() {
    assertEquals(MediaKind.HLS, MediaClassifier.classify("https://cdn.x/v/master.m3u8?token=1"))
    assertEquals(MediaKind.HLS, MediaClassifier.classify("https://cdn.x/play", "application/vnd.apple.mpegurl"))
    assertEquals(MediaKind.DASH, MediaClassifier.classify("https://cdn.x/v/manifest.mpd"))
    assertEquals(MediaKind.FILE, MediaClassifier.classify("https://cdn.x/movies/big.mp4"))
    assertEquals(MediaKind.FILE, MediaClassifier.classify("https://cdn.x/stream?id=3", "video/mp4"))
    assertEquals(MediaKind.FILE, MediaClassifier.classify("https://cdn.x/fragrance.mp4"))
    assertEquals(MediaKind.FILE, MediaClassifier.classify("https://cdn.x/chunky-bacon.webm"))
  }

  @Test
  fun `skips segments and non-media`() {
    assertNull(MediaClassifier.classify("https://cdn.x/v/seg-12.ts"))
    assertNull(MediaClassifier.classify("https://cdn.x/v/chunk_3.m4s"))
    assertNull(MediaClassifier.classify("https://cdn.x/v/segment_00012_av.mp4"))
    assertNull(MediaClassifier.classify("https://cdn.x/v/init.mp4"))
    assertNull(MediaClassifier.classify("https://cdn.x/v/video.mp4/range/0-1000"))
    assertNull(MediaClassifier.classify("https://cdn.x/app.js"))
    assertNull(MediaClassifier.classify("blob:https://x/1234"))
    assertNull(MediaClassifier.classify("https://cdn.x/a", "video/mp2t"))
  }

  @Test
  fun `audio only and dedup`() {
    assertTrue(MediaClassifier.isAudioOnly("https://x/song.mp3"))
    assertFalse(MediaClassifier.isAudioOnly("https://x/list.m3u8", "audio/mpegurl"))
    assertEquals(
      MediaClassifier.dedupKey("https://x/v.mp4?b=2&a=1"),
      MediaClassifier.dedupKey("http://x/v.mp4?a=1&range=0-100&b=2#t=5"),
    )
  }

  @Test
  fun `youtube stays on the page`() {
    assertTrue(MediaClassifier.isBlockedForPlayback("https://m.youtube.com/watch?v=1"))
    assertTrue(MediaClassifier.isBlockedForPlayback("https://youtu.be/abc"))
    assertFalse(MediaClassifier.isBlockedForPlayback("https://notyoutube.example/"))
  }

  @Test
  fun `takeover decisions`() {
    val page = "https://site.example/watch"
    val d = PlayTakeover::decide
    assertEquals(PlayTakeover.Action.ASK, d(VideoTapMode.ASK_ONCE_PER_SITE, null, page, true, false))
    assertEquals(PlayTakeover.Action.PLAY_IN_MPVGO, d(VideoTapMode.ASK_ONCE_PER_SITE, SiteChoice.MPVGO, page, true, false))
    assertEquals(PlayTakeover.Action.ASK, d(VideoTapMode.ASK_ONCE_PER_SITE, SiteChoice.MPVGO, page, false, false))
    assertEquals(PlayTakeover.Action.LET_PAGE_PLAY, d(VideoTapMode.ASK_ONCE_PER_SITE, SiteChoice.WEB, page, true, false))
    assertEquals(PlayTakeover.Action.ASK, d(VideoTapMode.ALWAYS_ASK, SiteChoice.MPVGO, page, true, false))
    assertEquals(PlayTakeover.Action.PLAY_IN_MPVGO, d(VideoTapMode.ALWAYS_MPVGO, null, page, true, false))
    assertEquals(PlayTakeover.Action.LET_PAGE_PLAY, d(VideoTapMode.ALWAYS_WEB, SiteChoice.MPVGO, page, true, false))
    assertEquals(PlayTakeover.Action.LET_PAGE_PLAY, d(VideoTapMode.ALWAYS_MPVGO, null, page, true, true))
    assertEquals(PlayTakeover.Action.LET_PAGE_PLAY, d(VideoTapMode.ALWAYS_MPVGO, null, "https://www.youtube.com/watch", true, false))
  }

  @Test
  fun `site choices round trip`() {
    var set = PlayTakeover.withChoice(emptySet(), "Site.Example", SiteChoice.MPVGO)
    assertEquals(SiteChoice.MPVGO, PlayTakeover.choiceFor("site.example", set))
    set = PlayTakeover.withChoice(set, "site.example", SiteChoice.WEB)
    assertEquals(setOf("site.example=WEB"), set)
    assertNull(PlayTakeover.choiceFor("other.example", set))
    assertNull(PlayTakeover.choiceFor("ite.example", set))
    set = PlayTakeover.withChoice(set, "site.example", null)
    assertTrue(set.isEmpty())
  }

  private fun media(
    url: String,
    kind: MediaKind,
    seq: Long,
    audio: Boolean = false,
    variants: Int = 0,
  ) = DetectedMedia(
    url = url,
    kind = kind,
    mime = null,
    frameUrl = "",
    pageUrl = "",
    pageTitle = "",
    size = -1,
    audioOnly = audio,
    variants = List(variants) { app.arju.mpvgo.downloader.engine.HlsPlaylist.Variant("https://x/$it.m3u8", 1000L * it, 0, 360 * (it + 1), null) },
    seq = seq,
  )

  @Test
  fun `best prefers master playlists then video then newest`() {
    val file = media("https://x/a.mp4", MediaKind.FILE, 5)
    val hls = media("https://x/b.m3u8", MediaKind.HLS, 1)
    val master = media("https://x/m.m3u8", MediaKind.HLS, 0, variants = 2)
    val audio = media("https://x/s.mp3", MediaKind.FILE, 9, audio = true)
    assertEquals(master, MediaSniffer.best(listOf(file, hls, master, audio)))
    assertEquals(hls, MediaSniffer.best(listOf(file, hls, audio)))
    assertEquals(file, MediaSniffer.best(listOf(file, audio)))
    assertEquals("720p", master.quality)
    assertNull(MediaSniffer.best(emptyList()))
  }

  @Test
  fun `address bar input`() {
    assertEquals("https://example.com", UrlInput.toUrl("example.com"))
    assertEquals("https://example.com/a?b", UrlInput.toUrl(" example.com/a?b "))
    assertEquals("http://10.0.2.2:8000/x", UrlInput.toUrl("http://10.0.2.2:8000/x"))
    assertEquals("http://10.0.2.2:8000", UrlInput.toUrl("10.0.2.2:8000"))
    assertEquals("http://localhost/x", UrlInput.toUrl("localhost/x"))
    assertEquals("https://duckduckgo.com/?q=cat+videos", UrlInput.toUrl("cat videos"))
    assertEquals("https://duckduckgo.com/?q=hello", UrlInput.toUrl("hello"))
  }

  @Test
  fun `tab navigation steps forward and backward and detects boundaries`() {
    val tabs = listOf(101L, 102L, 103L, 104L)
    assertEquals(102L, TabNavigation.stepId(101L, tabs, 1))
    assertEquals(103L, TabNavigation.stepId(102L, tabs, 1))
    assertEquals(104L, TabNavigation.stepId(103L, tabs, 1))
    assertNull(TabNavigation.stepId(104L, tabs, 1))

    assertEquals(103L, TabNavigation.stepId(104L, tabs, -1))
    assertEquals(102L, TabNavigation.stepId(103L, tabs, -1))
    assertEquals(101L, TabNavigation.stepId(102L, tabs, -1))
    assertNull(TabNavigation.stepId(101L, tabs, -1))

    assertNull(TabNavigation.stepId(999L, tabs, 1))
    assertNull(TabNavigation.stepId(null, tabs, 1))
    assertNull(TabNavigation.stepId(101L, emptyList<Long>(), 1))
  }
}
