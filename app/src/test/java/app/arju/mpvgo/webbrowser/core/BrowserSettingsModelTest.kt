package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.webbrowser.settings.BrowserSettingsCategory
import app.arju.mpvgo.webbrowser.settings.BrowserSettingsIndex
import app.arju.mpvgo.webbrowser.settings.adBlockSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSettingsModelTest {
  @Test
  fun `http pages upgrade to https`() {
    assertEquals("https://example.com/a?b=1", HttpsUpgrade.upgrade("http://example.com/a?b=1", emptySet()))
    assertEquals("https://Example.com", HttpsUpgrade.upgrade("HTTP://Example.com", emptySet()))
  }

  @Test
  fun `https, local addresses and known http-only hosts are left alone`() {
    assertNull(HttpsUpgrade.upgrade("https://example.com", emptySet()))
    assertNull(HttpsUpgrade.upgrade("http://10.0.2.2:8766/v.mp4", emptySet()))
    assertNull(HttpsUpgrade.upgrade("http://localhost:8080", emptySet()))
    assertNull(HttpsUpgrade.upgrade("http://[::1]:80/", emptySet()))
    assertNull(HttpsUpgrade.upgrade("http://router/", emptySet()))
    assertNull(HttpsUpgrade.upgrade("http://nas.local/x", emptySet()))
    assertNull(HttpsUpgrade.upgrade("http://box.home.arpa/", emptySet()))
    assertNull(HttpsUpgrade.upgrade("http://old.example.com/", setOf("old.example.com")))
    assertNull(HttpsUpgrade.upgrade("ftp://example.com", emptySet()))
  }

  @Test
  fun `host parsing strips port, credentials and path`() {
    assertEquals("example.com", HttpsUpgrade.hostOf("http://user:pw@Example.com:8080/p?q#f"))
    assertEquals("[::1]", HttpsUpgrade.hostOf("http://[::1]:80/"))
    assertNull(HttpsUpgrade.hostOf("about:blank"))
    assertEquals("http://example.com/x", HttpsUpgrade.downgrade("https://example.com/x"))
  }

  @Test
  fun `suggestions put the copied link first, drop duplicates and stop at the max`() {
    val bookmarks = listOf("A" to "https://a.com/", "B" to "https://b.com")
    val history = listOf("A again" to "https://a.com", "C" to "https://c.com", "D" to "https://d.com")
    val out = Suggestions.merge("https://b.com/", bookmarks, history, max = 3)
    assertEquals(listOf("https://b.com/", "https://a.com/", "https://c.com"), out.map { it.url })
    assertEquals(UrlSuggestion.Kind.COPIED, out[0].kind)
    assertEquals(UrlSuggestion.Kind.BOOKMARK, out[1].kind)
    assertEquals(UrlSuggestion.Kind.HISTORY, out[2].kind)
  }

  @Test
  fun `only a single web link counts as a copied link`() {
    assertEquals("https://x.com/v?id=1", ClipboardLink.extract("  https://x.com/v?id=1 \n"))
    assertNull(ClipboardLink.extract("look at https://x.com"))
    assertNull(ClipboardLink.extract("ftp://x.com"))
    assertNull(ClipboardLink.extract(null))
    assertNull(ClipboardLink.extract("https://x.com/" + "a".repeat(2100)))
  }

  @Test
  fun `top bar picker keeps at most two buttons and reorders them`() {
    val two = listOf(TopBarButton.BOOKMARKS, TopBarButton.DOWNLOADS)
    assertEquals(two, TopBarButton.toggle(two, TopBarButton.HOME))
    assertEquals(listOf(TopBarButton.DOWNLOADS), TopBarButton.toggle(two, TopBarButton.BOOKMARKS))
    assertEquals(listOf(TopBarButton.DOWNLOADS, TopBarButton.HOME), TopBarButton.toggle(listOf(TopBarButton.DOWNLOADS), TopBarButton.HOME))
    assertEquals(two.reversed(), TopBarButton.move(two, 0, 1))
    assertEquals(two, TopBarButton.move(two, 0, 5))
    assertEquals("HOME,FIND", TopBarButton.encode(listOf(TopBarButton.HOME, TopBarButton.FIND, TopBarButton.SHARE)))
    assertEquals(listOf(TopBarButton.HOME, TopBarButton.FIND), TopBarButton.parse("HOME,FIND"))
  }

  @Test
  fun `settings search matches titles, pages and keywords`() {
    assertTrue(BrowserSettingsIndex.search("  ").isEmpty())
    assertEquals(listOf("Always use secure connections"), BrowserSettingsIndex.search("https").map { it.title })
    assertTrue(BrowserSettingsIndex.search("cookies").any { it.title == "Block third-party cookies" })
    assertTrue(BrowserSettingsIndex.search("privacy dnt").all { it.category == BrowserSettingsCategory.PRIVACY })
    assertTrue(BrowserSettingsIndex.search("zzz").isEmpty())
  }

  @Test
  fun `ad block summary`() {
    assertEquals("Off", adBlockSummary(false, 4, 10))
    assertEquals("On · 1 filter list · 0 blocked", adBlockSummary(true, 1, 0))
    assertEquals("On · 4 filter lists · 12 blocked", adBlockSummary(true, 4, 12))
  }

  @Test
  fun `privacy headers only when asked`() {
    assertTrue(PrivacyHeaders.of(false).isEmpty())
    assertEquals("1", PrivacyHeaders.of(true)["DNT"])
  }
}
