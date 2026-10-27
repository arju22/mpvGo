package app.arju.mpvgo.webbrowser.library

import app.arju.mpvgo.webbrowser.core.SavedSession
import app.arju.mpvgo.webbrowser.core.SavedTab
import app.arju.mpvgo.webbrowser.core.TabSession
import app.arju.mpvgo.webbrowser.library.NetscapeBookmarks.Folder
import app.arju.mpvgo.webbrowser.library.NetscapeBookmarks.Link
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class BrowserLibraryTest {
  private val chromeExport =
    """
    <!DOCTYPE NETSCAPE-Bookmark-file-1>
    <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
    <TITLE>Bookmarks</TITLE>
    <H1>Bookmarks</H1>
    <DL><p>
        <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Bookmarks bar</H3>
        <DL><p>
            <DT><A HREF="https://vimeo.com/channels/trailers" ADD_DATE="1700000100" ICON="data:image/png;base64,AAA">Trailers &amp; more</A>
            <DT><H3>Music</H3>
            <DL><p>
                <DT><A HREF="https://example.com/a?x=1&amp;y=2">A &#38; B &lt;live&gt;</A>
            </DL><p>
        </DL><p>
        <DT><A HREF="https://archive.org/">Archive</A>
        <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
    </DL><p>
    """.trimIndent()

  @Test
  fun `chrome export parses folders, links, dates and entities`() {
    val nodes = NetscapeBookmarks.parse(chromeExport)
    assertEquals(2, nodes.size)
    val bar = nodes[0] as Folder
    assertEquals("Bookmarks bar", bar.title)
    assertEquals(Link("Trailers & more", "https://vimeo.com/channels/trailers", 1_700_000_100_000), bar.children[0])
    val music = bar.children[1] as Folder
    assertEquals(Link("A & B <live>", "https://example.com/a?x=1&y=2"), music.children.single())
    assertEquals(Link("Archive", "https://archive.org/"), nodes[1])
  }

  @Test
  fun `export round trips through parse`() {
    val tree =
      listOf(
        Folder("Movie \"sites\"", listOf(Link("R & M", "https://r.com/?a=1&b=2", 1_600_000_000_000), Folder("Empty", emptyList()))),
        Link("Top", "https://top.example/"),
      )
    assertEquals(tree, NetscapeBookmarks.parse(NetscapeBookmarks.export(tree)))
  }

  @Test
  fun `garbage input gives nothing`() {
    assertTrue(NetscapeBookmarks.parse("hello <b>world</b>").isEmpty())
  }

  @Test
  fun `history sections follow calendar days`() {
    val zone = ZoneOffset.UTC
    val now = LocalDateTime.of(2026, 3, 10, 9, 0).toInstant(zone).toEpochMilli()

    fun at(
      day: Int,
      hour: Int,
    ) = LocalDateTime.of(2026, 3, day, hour, 0).toInstant(zone).toEpochMilli()
    assertEquals(HistoryGroups.Section.TODAY, HistoryGroups.section(at(10, 0), now, zone))
    assertEquals(HistoryGroups.Section.YESTERDAY, HistoryGroups.section(at(9, 23), now, zone))
    assertEquals(HistoryGroups.Section.WEEK, HistoryGroups.section(at(4, 12), now, zone))
    assertEquals(HistoryGroups.Section.MONTH, HistoryGroups.section(at(1, 12), now, zone))
    assertEquals(HistoryGroups.Section.OLDER, HistoryGroups.section(LocalDateTime.of(2025, 12, 1, 0, 0).toInstant(zone).toEpochMilli(), now, zone))
    assertEquals("23:00", HistoryGroups.label(at(9, 23), now, zone, Locale.UK))
    assertEquals("11:00 PM", HistoryGroups.label(at(9, 23), now, zone, Locale.US, is24h = false))
    assertEquals("1 Dec 2025", HistoryGroups.label(LocalDateTime.of(2025, 12, 1, 0, 0).toInstant(zone).toEpochMilli(), now, zone, Locale.UK))

    val grouped = HistoryGroups.group(listOf(at(10, 8), at(10, 1), at(9, 5), at(5, 5)), { it }, now, zone)
    assertEquals(listOf(HistoryGroups.Section.TODAY, HistoryGroups.Section.YESTERDAY, HistoryGroups.Section.WEEK), grouped.map { it.first })
    assertEquals(listOf(at(10, 8), at(10, 1)), grouped[0].second)
  }

  @Test
  fun `clear ranges`() {
    val zone = ZoneOffset.UTC
    val now = LocalDateTime.of(2026, 3, 10, 9, 30).toInstant(zone).toEpochMilli()
    assertEquals(now - 3_600_000, ClearRange.LAST_HOUR.since(now, zone))
    assertEquals(LocalDateTime.of(2026, 3, 10, 0, 0).toInstant(zone).toEpochMilli(), ClearRange.TODAY.since(now, zone))
    assertEquals(0L, ClearRange.ALL_TIME.since(now, zone))
  }

  @Test
  fun `bookmark tree helpers`() {
    val all =
      listOf(
        BookmarkEntity(1, 0, true, "Movies", createdAt = 1),
        BookmarkEntity(2, 1, true, "Trailers", createdAt = 2),
        BookmarkEntity(3, 2, false, "T1", "https://t1", 10),
        BookmarkEntity(4, 0, false, "Top", "https://top", 5),
        BookmarkEntity(5, 0, true, "Audio", createdAt = 3),
      )
    assertEquals(setOf(1L, 2L, 3L), BookmarkTree.withDescendants(listOf(1L), all))
    assertEquals(
      listOf(null to 0, "Audio" to 1, "Movies" to 1, "Trailers" to 2),
      BookmarkTree.folders(all).map { it.first?.title to it.second },
    )
    assertEquals(listOf(null to 0, "Audio" to 1), BookmarkTree.folders(all, exclude = setOf(1L)).map { it.first?.title to it.second })
    assertEquals(listOf("Movies", "Trailers"), BookmarkTree.path(2, all).map { it.title })
    val nodes = BookmarkTree.toNodes(all)
    assertEquals(listOf("Movies", "Audio", "Top"), nodes.map { it.title })
    assertEquals(Link("T1", "https://t1", 10), ((nodes[0] as Folder).children.single() as Folder).children.single())
  }

  @Test
  fun `history only records web pages`() {
    assertTrue(BrowserLibrary.isRecordable("https://a.com"))
    assertTrue(!BrowserLibrary.isRecordable("about:blank"))
    assertTrue(!BrowserLibrary.isRecordable("data:text/html,x"))
  }

  @Test
  fun `tab session round trips and clamps the active index`() {
    val session = SavedSession(listOf(SavedTab("https://a.com", "A", true, 120), SavedTab("")), active = 1)
    assertEquals(session, TabSession.decode(TabSession.encode(session)))
    assertEquals(1, TabSession.decode("""{"tabs":[{"url":"x"},{"url":"y"}],"active":9}""")!!.active)
    assertNull(TabSession.decode("broken"))
    assertNull(TabSession.decode(""))
  }
}
