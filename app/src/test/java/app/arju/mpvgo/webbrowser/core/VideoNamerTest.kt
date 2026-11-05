package app.arju.mpvgo.webbrowser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoNamerTest {
  @Test
  fun `cleans branding suffixes`() {
    assertEquals("Big Buck Bunny", VideoNamer.cleanTitle("Big Buck Bunny - YouTube"))
    assertEquals("Sintel Trailer", VideoNamer.cleanTitle("Sintel Trailer | Official Site"))
    assertEquals("Elephant Dreams", VideoNamer.cleanTitle("Elephant Dreams • Vimeo"))
    assertEquals("Wildlife Documentary", VideoNamer.cleanTitle("Wildlife Documentary - Watch Online"))
  }

  @Test
  fun `meaningful file names`() {
    assertFalse(VideoNamer.isMeaningfulFileName("master.m3u8"))
    assertFalse(VideoNamer.isMeaningfulFileName("index.mpd"))
    assertFalse(VideoNamer.isMeaningfulFileName("video_1.mp4"))
    assertFalse(VideoNamer.isMeaningfulFileName("1234567890abcdef12345678.mp4"))
    assertTrue(VideoNamer.isMeaningfulFileName("trailer.mp4"))
    assertTrue(VideoNamer.isMeaningfulFileName("big_buck_bunny_720p.mp4"))
  }

  @Test
  fun `names media using page title when available`() {
    val media = DetectedMedia(
      url = "https://cdn.example.com/stream/master.m3u8",
      pageTitle = "Big Buck Bunny – Official Trailer - YouTube",
      pageUrl = "https://example.com/watch",
      kind = MediaKind.HLS,
    )
    assertEquals("Big Buck Bunny – Official Trailer", VideoNamer.nameFor(media))
  }

  @Test
  fun `falls back to filename when page title is generic`() {
    val media = DetectedMedia(
      url = "https://cdn.example.com/files/nature_walk.mp4",
      pageTitle = "Video Player",
      pageUrl = "https://example.com/player",
      kind = MediaKind.FILE,
    )
    assertEquals("nature_walk", VideoNamer.nameFor(media))
  }

  @Test
  fun `falls back to host when page title and filename are generic`() {
    val media = DetectedMedia(
      url = "https://cdn.example.com/master.m3u8",
      pageTitle = "Loading...",
      pageUrl = "https://archive.org/details/sample",
      kind = MediaKind.HLS,
    )
    assertEquals("archive.org", VideoNamer.nameFor(media))
  }

  @Test
  fun `disambiguates multiple streams`() {
    val media1 = DetectedMedia(
      url = "https://cdn.example.com/1080.mp4",
      pageTitle = "Concert Live",
      pageUrl = "https://example.com",
      kind = MediaKind.FILE,
      height = 1080,
    )
    val media2 = DetectedMedia(
      url = "https://cdn.example.com/720.mp4",
      pageTitle = "Concert Live",
      pageUrl = "https://example.com",
      kind = MediaKind.FILE,
      height = 720,
    )
    assertEquals("Concert Live – 1080p", VideoNamer.nameFor(media1, index = 0, totalWithSameName = 2))
    assertEquals("Concert Live – 720p", VideoNamer.nameFor(media2, index = 1, totalWithSameName = 2))
  }
}
