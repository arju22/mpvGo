package app.arju.mpvgo.downloader

import app.arju.mpvgo.downloader.engine.HlsPlaylist
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistTest {
  private val base = "https://cdn.example.com/v/master.m3u8?token=abc"

  @Test
  fun `master playlist variants sorted best first with resolved urls`() {
    val text = """
      #EXTM3U
      #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.4d401e,mp4a.40.2"
      360/index.m3u8
      #EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
      https://other.example.com/1080.m3u8
      #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720
      /abs/720.m3u8
    """.trimIndent()
    val m = HlsPlaylist.parse(text, base) as HlsPlaylist.Master
    assertEquals(listOf(1080, 720, 360), m.variants.map { it.height })
    assertEquals("https://other.example.com/1080.m3u8", m.variants[0].url)
    assertEquals("https://cdn.example.com/abs/720.m3u8", m.variants[1].url)
    assertEquals("https://cdn.example.com/v/360/index.m3u8", m.variants[2].url)
    assertEquals("avc1.4d401e,mp4a.40.2", m.variants[2].codecs)
    assertEquals("720p", m.variants[1].label)
  }

  @Test
  fun `choose picks closest height not above preference`() {
    val v = (HlsPlaylist.parse(
      "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=1920x1080\na\n#EXT-X-STREAM-INF:BANDWIDTH=1,RESOLUTION=854x480\nb\n",
      base,
    ) as HlsPlaylist.Master).variants
    assertEquals(480, HlsPlaylist.choose(v, 720)!!.height)
    assertEquals(1080, HlsPlaylist.choose(v, null)!!.height)
    assertEquals(480, HlsPlaylist.choose(v, 240)!!.height)
    assertNull(HlsPlaylist.choose(emptyList(), 720))
  }

  @Test
  fun `media playlist with aes keys sequence and end`() {
    val text = """
      #EXTM3U
      #EXT-X-TARGETDURATION:10
      #EXT-X-MEDIA-SEQUENCE:7
      #EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x000102030405060708090a0b0c0d0e0f
      #EXTINF:9.5,
      seg7.ts
      #EXT-X-KEY:METHOD=AES-128,URI="https://keys.example.com/k2"
      #EXTINF:10.0,
      seg8.ts
      #EXT-X-KEY:METHOD=NONE
      #EXTINF:4,
      seg9.ts
      #EXT-X-ENDLIST
    """.trimIndent()
    val media = (HlsPlaylist.parse(text, base) as HlsPlaylist.MediaPlaylist).media
    assertEquals(3, media.segments.size)
    assertFalse(media.isLive)
    assertEquals(23.5, media.totalDuration, 0.001)
    val s0 = media.segments[0]
    assertEquals("https://cdn.example.com/v/seg7.ts", s0.url)
    assertEquals("https://cdn.example.com/v/key.bin", s0.key!!.uri)
    assertArrayEquals(ByteArray(16) { it.toByte() }, HlsPlaylist.ivFor(s0))
    val s1 = media.segments[1]
    assertEquals(8L, s1.sequence)
    val iv = HlsPlaylist.ivFor(s1)
    assertEquals(8.toByte(), iv[15])
    assertEquals(0.toByte(), iv[0])
    assertNull(media.segments[2].key)
    assertFalse(media.hasUnsupportedEncryption)
  }

  @Test
  fun `live and sample aes are detected`() {
    val live = HlsPlaylist.parseMedia("#EXTM3U\n#EXTINF:2,\na.ts\n", base)
    assertTrue(live.isLive)
    val drm = HlsPlaylist.parseMedia("#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"skd://x\"\n#EXTINF:2,\na.ts\n#EXT-X-ENDLIST\n", base)
    assertTrue(drm.hasUnsupportedEncryption)
  }

  @Test
  fun `fmp4 init map is resolved`() {
    val media = HlsPlaylist.parseMedia("#EXTM3U\n#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:2,\na.m4s\n#EXT-X-ENDLIST\n", base)
    assertEquals("https://cdn.example.com/v/init.mp4", media.initUrl)
  }

  @Test
  fun `attribute parser handles quoted commas`() {
    val a = HlsPlaylist.attributes("BANDWIDTH=1,CODECS=\"a,b\",NAME=x")
    assertEquals("a,b", a["CODECS"])
    assertEquals("x", a["NAME"])
    assertEquals("1", a["BANDWIDTH"])
  }

  @Test
  fun `playlist sniffing`() {
    assertTrue(HlsPlaylist.isPlaylist("\uFEFF#EXTM3U\n"))
    assertFalse(HlsPlaylist.isPlaylist("<html>"))
  }
}
