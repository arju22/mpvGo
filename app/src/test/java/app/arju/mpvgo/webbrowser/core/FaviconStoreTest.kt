package app.arju.mpvgo.webbrowser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaviconStoreTest {

  @Test
  fun testCleanHost() {
    assertEquals("youtube.com", FaviconStore.cleanHost("https://www.youtube.com/watch?v=123"))
    assertEquals("github.com", FaviconStore.cleanHost("http://github.com:8080/path"))
    assertEquals("reddit.com", FaviconStore.cleanHost("WWW.REDDIT.COM"))
    assertEquals("en.wikipedia.org", FaviconStore.cleanHost("https://en.wikipedia.org/wiki/Main_Page"))
    assertEquals("example.com", FaviconStore.cleanHost("example.com"))
  }

  @Test
  fun testHostColorDeterministic() {
    val color1 = FaviconStore.hostColor("youtube.com")
    val color2 = FaviconStore.hostColor("youtube.com")
    assertEquals(color1, color2)

    val colorReddit = FaviconStore.hostColor("reddit.com")
    // Different hosts should generally map to different colors
    assertTrue(color1 != colorReddit)
  }

  @Test
  fun testResolveUrl() {
    val base = "https://example.com/sub/page.html"
    assertEquals("https://example.com/favicon.ico", FaviconStore.resolveUrl(base, "/favicon.ico"))
    assertEquals("https://example.com/sub/icon.png", FaviconStore.resolveUrl(base, "icon.png"))
    assertEquals("https://cdn.example.com/icon.png", FaviconStore.resolveUrl(base, "//cdn.example.com/icon.png"))
    assertEquals("https://other.com/icon.png", FaviconStore.resolveUrl(base, "https://other.com/icon.png"))
  }

  @Test
  fun testParseIconLinks() {
    val html = """
      <!DOCTYPE html>
      <html>
      <head>
        <link rel="stylesheet" href="/style.css">
        <link rel="icon" type="image/png" sizes="32x32" href="/favicon-32x32.png">
        <link rel="apple-touch-icon" sizes="180x180" href="/apple-touch-icon.png">
        <link rel="shortcut icon" href="data:image/x-icon;base64,AAABAA">
      </head>
      <body></body>
      </html>
    """.trimIndent()

    val candidates = FaviconStore.parseIconLinks(html, "https://example.com/")
    assertEquals(2, candidates.size)

    val icon32 = candidates.find { it.size == 32 }
    assertTrue(icon32 != null)
    assertEquals("https://example.com/favicon-32x32.png", icon32?.url)

    val icon180 = candidates.find { it.size == 180 }
    assertTrue(icon180 != null)
    assertEquals("https://example.com/apple-touch-icon.png", icon180?.url)
  }
}
