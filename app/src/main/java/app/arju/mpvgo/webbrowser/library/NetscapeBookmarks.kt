package app.arju.mpvgo.webbrowser.library

/**
 * Reads and writes the Netscape bookmark file format that Chrome, Firefox and most browsers
 * use for HTML bookmark import and export.
 */
object NetscapeBookmarks {
  sealed interface Node {
    val title: String
  }

  data class Folder(override val title: String, val children: List<Node>) : Node

  data class Link(override val title: String, val url: String, val addedAt: Long = 0) : Node

  private val TOKEN =
    Regex(
      "<DT>\\s*<H3[^>]*>(.*?)</H3>|<DT>\\s*<A\\s([^>]*)>(.*?)</A>|<DL[^>]*>|</DL>",
      setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
  private val HREF = Regex("HREF\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE)
  private val ADD_DATE = Regex("ADD_DATE\\s*=\\s*\"(\\d+)\"", RegexOption.IGNORE_CASE)

  /** The top-level items of [html]. Unknown markup is ignored. */
  fun parse(html: String): List<Node> {
    class Level(val title: String?) {
      val items = mutableListOf<Node>()
    }
    val stack = ArrayDeque<Level>()
    val root = Level(null)
    stack.addLast(root)
    var pendingFolder: String? = null
    var seenRootList = false
    for (m in TOKEN.findAll(html)) {
      val text = m.value
      when {
        m.groups[1] != null -> pendingFolder = decode(m.groupValues[1]).trim()
        m.groups[2] != null -> {
          val attrs = m.groupValues[2]
          val url = HREF.find(attrs)?.groupValues?.get(1)?.let(::decode)?.trim().orEmpty()
          if (url.isNotEmpty() && !url.startsWith("javascript:", ignoreCase = true) && !url.startsWith("place:")) {
            val added = ADD_DATE.find(attrs)?.groupValues?.get(1)?.toLongOrNull()?.times(1000) ?: 0
            stack.last().items += Link(decode(m.groupValues[3]).trim().ifEmpty { url }, url, added)
          }
        }
        text.startsWith("</") -> {
          if (stack.size > 1) {
            val done = stack.removeLast()
            stack.last().items += Folder(done.title.orEmpty(), done.items.toList())
          }
        }
        else -> {
          // The first <DL> is the file's own list; later ones open the folder named just before.
          if (!seenRootList && pendingFolder == null) {
            seenRootList = true
          } else {
            stack.addLast(Level(pendingFolder ?: ""))
          }
          pendingFolder = null
        }
      }
    }
    while (stack.size > 1) {
      val done = stack.removeLast()
      stack.last().items += Folder(done.title.orEmpty(), done.items.toList())
    }
    return root.items
  }

  fun export(nodes: List<Node>): String =
    buildString {
      append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
      append("<!-- This is an automatically generated file. -->\n")
      append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
      append("<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
      write(nodes, 1)
      append("</DL><p>\n")
    }

  private fun StringBuilder.write(
    nodes: List<Node>,
    depth: Int,
  ) {
    val pad = "    ".repeat(depth)
    for (node in nodes) {
      when (node) {
        is Folder -> {
          append(pad).append("<DT><H3>").append(encode(node.title)).append("</H3>\n")
          append(pad).append("<DL><p>\n")
          write(node.children, depth + 1)
          append(pad).append("</DL><p>\n")
        }
        is Link -> {
          append(pad).append("<DT><A HREF=\"").append(encode(node.url)).append('"')
          if (node.addedAt > 0) append(" ADD_DATE=\"").append(node.addedAt / 1000).append('"')
          append('>').append(encode(node.title)).append("</A>\n")
        }
      }
    }
  }

  private fun encode(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos|nbsp);")

  private fun decode(s: String): String =
    ENTITY.replace(s.replace(Regex("<[^>]*>"), "")) { m ->
      when (val e = m.groupValues[1]) {
        "amp" -> "&"
        "lt" -> "<"
        "gt" -> ">"
        "quot" -> "\""
        "apos" -> "'"
        "nbsp" -> " "
        else -> {
          val code = if (e.startsWith("#x")) e.substring(2).toIntOrNull(16) else e.substring(1).toIntOrNull()
          code?.let { String(Character.toChars(it)) } ?: m.value
        }
      }
    }
}
