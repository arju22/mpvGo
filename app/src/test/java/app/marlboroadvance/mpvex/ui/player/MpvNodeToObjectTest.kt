package app.marlboroadvance.mpvex.ui.player

import `is`.xyz.mpv.MPVNode
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvNodeToObjectTest {
  private val json = Json {
    isLenient = true
    ignoreUnknownKeys = true
  }

  private fun track(id: Long, title: String, extra: Map<String, MPVNode> = emptyMap()) =
    MPVNode.MapNode(
      mapOf(
        "id" to MPVNode.IntNode(id),
        "type" to MPVNode.StringNode("sub"),
        "title" to MPVNode.StringNode(title),
        "selected" to MPVNode.BooleanNode(true),
        "demux-fps" to MPVNode.DoubleNode(23.976),
      ) + extra,
    )

  private fun decodeTitles(vararg titles: String): List<String?> {
    val node = MPVNode.ArrayNode(titles.mapIndexed { i, t -> track(i + 1L, t) }.toTypedArray())
    return node.toObject<List<TrackNode>>(json).map { it.title }
  }

  @Test
  fun `plain titles decode`() {
    assertEquals(listOf("English", "Signs & Songs"), decodeTitles("English", "Signs & Songs"))
  }

  @Test
  fun `backslashes quotes and control chars survive (#755)`() {
    val titles = arrayOf(
      "Signs\\Songs",
      "ends with backslash\\",
      "quoted \\\"inner\\\" text",
      "say \"hi\"",
      "line1\nline2\ttab",
      "C:\\new\\folder\\",
      "\\u00zz not unicode",
      "日本語 字幕",
    )
    assertEquals(titles.toList(), decodeTitles(*titles))
  }

  @Test
  fun `metadata map, external filename and missing optional fields`() {
    val node = MPVNode.ArrayNode(
      arrayOf(
        track(
          1,
          "a",
          mapOf(
            "external-filename" to MPVNode.StringNode("/sdcard/Sub\\dir\\x.srt"),
            "metadata" to MPVNode.MapNode(mapOf("key\"q" to MPVNode.StringNode("v\\"))),
            "image" to MPVNode.None,
          ),
        ),
        MPVNode.MapNode(mapOf("id" to MPVNode.IntNode(2), "type" to MPVNode.StringNode("audio"))),
      ),
    )
    val tracks = node.toObject<List<TrackNode>>(json)
    assertEquals("/sdcard/Sub\\dir\\x.srt", tracks[0].externalFilename)
    assertEquals(mapOf("key\"q" to "v\\"), tracks[0].metadata)
    assertNull(tracks[0].image)
    assertEquals(true, tracks[1].isAudio)
  }

  @Test
  fun `chapters decode`() {
    val node = MPVNode.ArrayNode(
      arrayOf(
        MPVNode.MapNode(mapOf("time" to MPVNode.DoubleNode(0.0), "title" to MPVNode.StringNode("Intro\\"))),
        MPVNode.MapNode(mapOf("time" to MPVNode.IntNode(90), "title" to MPVNode.StringNode("Part \"2\""))),
      ),
    )
    val chapters = node.toObject<List<ChapterNode>>(json)
    assertEquals(listOf("Intro\\", "Part \"2\""), chapters.map { it.title })
    assertEquals(90f, chapters[1].time)
  }
}
