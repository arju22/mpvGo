package app.arju.mpvgo.webbrowser.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StartPageModelTest {
  @Test
  fun `blank or broken speed dials fall back to defaults`() {
    assertEquals(SpeedDials.defaults, SpeedDials.decode(""))
    assertEquals(SpeedDials.defaults, SpeedDials.decode("not json"))
  }

  @Test
  fun `speed dials round trip and an empty list stays empty`() {
    val list = listOf(SpeedDial("A", "https://a.com"), SpeedDial("B", "https://b.org/x"))
    assertEquals(list, SpeedDials.decode(SpeedDials.encode(list)))
    assertTrue(SpeedDials.decode(SpeedDials.encode(emptyList())).isEmpty())
  }

  @Test
  fun `speed dials are capped and nameless entries use the host`() {
    val many = (1..12).map { SpeedDial("S$it", "https://s$it.com") }
    assertEquals(SpeedDials.MAX, SpeedDials.decode(SpeedDials.encode(many)).size)
    val decoded = SpeedDials.decode("""[{"n":"","u":"https://www.example.com/a"},{"n":"x","u":""}]""")
    assertEquals(listOf(SpeedDial("example.com", "https://www.example.com/a")), decoded)
  }

  @Test
  fun `search engine templates`() {
    assertEquals("https://duckduckgo.com/?q=%s", SearchEngine.DUCKDUCKGO.template(""))
    assertEquals("https://x.test/s?q=%s", SearchEngine.CUSTOM.template(" https://x.test/s?q=%s "))
    assertEquals(SearchEngine.GOOGLE.template(""), SearchEngine.CUSTOM.template("https://x.test/no-placeholder"))
    assertEquals(
      "https://search.brave.com/search?q=cat+videos",
      UrlInput.toUrl("cat videos", SearchEngine.BRAVE.template("")),
    )
  }

  @Test
  fun `desktop user agent drops the Android platform and Mobile token`() {
    val mobile =
      "Mozilla/5.0 (Linux; Android 16; sdk_gphone64_arm64 Build/BP22.250325.006; wv) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Version/4.0 Chrome/133.0.6943.137 Mobile Safari/537.36"
    assertEquals(
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.6943.137 Safari/537.36",
      DesktopMode.userAgent(mobile),
    )
  }

  @Test
  fun `top bar buttons parse unknown and duplicates away and keep two`() {
    assertEquals(
      listOf(TopBarButton.HISTORY, TopBarButton.SHARE),
      TopBarButton.parse("HISTORY, bogus,HISTORY,SHARE,FIND"),
    )
    assertTrue(TopBarButton.parse("").isEmpty())
  }
}
