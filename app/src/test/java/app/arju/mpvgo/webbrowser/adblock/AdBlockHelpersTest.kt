package app.arju.mpvgo.webbrowser.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockHelpersTest {
  @Test
  fun requestTypeFromExtension() {
    assertEquals("script", RequestTypes.of("https://a.com/x/app.js?v=1", "*/*", false))
    assertEquals("stylesheet", RequestTypes.of("https://a.com/s.CSS", null, false))
    assertEquals("image", RequestTypes.of("https://a.com/i.webp#x", null, false))
    assertEquals("font", RequestTypes.of("https://a.com/f.woff2", null, false))
    assertEquals("media", RequestTypes.of("https://cdn.a.com/v/master.m3u8?token=1", null, false))
  }

  @Test
  fun requestTypeFromAcceptAndFlags() {
    assertEquals("document", RequestTypes.of("https://a.com/", "text/html", true))
    assertEquals("subdocument", RequestTypes.of("https://ads.com/frame", "text/html,application/xhtml+xml", false))
    assertEquals("image", RequestTypes.of("https://a.com/pixel", "image/avif,image/webp,*/*", false))
    assertEquals("xmlhttprequest", RequestTypes.of("https://a.com/api", "*/*", false, isXhr = true))
    assertEquals("other", RequestTypes.of("https://a.com/api", "*/*", false))
  }

  @Test
  fun hostOnlyUrlHasNoExtension() {
    assertEquals("", RequestTypes.extension("https://example.com"))
    assertEquals("", RequestTypes.extension("https://example.com/"))
    assertEquals("js", RequestTypes.extension("https://example.com/a.b/c.js"))
  }

  @Test
  fun siteMatching() {
    val sites = setOf("example.com", "https://www.news.org/path")
    assertTrue(SiteMatcher.isAllowed("https://example.com/a", sites))
    assertTrue(SiteMatcher.isAllowed("https://m.example.com/a", sites))
    assertTrue(SiteMatcher.isAllowed("http://user@news.org:8080/x", sites))
    assertFalse(SiteMatcher.isAllowed("https://notexample.com/", sites))
    assertFalse(SiteMatcher.isAllowed("https://example.com.evil.net/", sites))
    assertFalse(SiteMatcher.isAllowed("https://example.com/", emptySet()))
  }

  @Test
  fun customListsAreFilteredAndStable() {
    val lists = FilterLists.custom(listOf(" https://x.org/my.txt ", "https://x.org/my.txt", "ftp://bad", ""))
    assertEquals(1, lists.size)
    assertEquals("my.txt", lists[0].name)
    assertEquals(FilterLists.customId("https://x.org/my.txt"), lists[0].id)
  }

  @Test
  fun defaultListsAreAllEnabled() {
    assertEquals(setOf("easylist", "easyprivacy", "ublock", "cookies"), FilterLists.DEFAULT_ENABLED)
  }
}
