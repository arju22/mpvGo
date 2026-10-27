package app.arju.mpvgo.webbrowser.adblock

import java.util.Locale

/** A downloadable filter list. Lists are fetched at runtime, never bundled. */
data class FilterList(
  val id: String,
  val name: String,
  val url: String,
  val enabledByDefault: Boolean,
)

object FilterLists {
  val BUILT_IN =
    listOf(
      FilterList("easylist", "EasyList", "https://easylist.to/easylist/easylist.txt", true),
      FilterList("easyprivacy", "EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt", true),
      FilterList(
        "ublock",
        "uBlock filters",
        "https://ublockorigin.github.io/uAssets/filters/filters.txt",
        true,
      ),
      FilterList(
        "cookies",
        "Cookie notices",
        "https://secure.fanboy.co.nz/fanboy-cookiemonster.txt",
        true,
      ),
    )

  val DEFAULT_ENABLED: Set<String> = BUILT_IN.filter { it.enabledByDefault }.map { it.id }.toSet()

  /** Stable file id for a custom list URL. */
  fun customId(url: String): String = "custom_" + Integer.toHexString(url.trim().hashCode())

  /** Custom list entries from the user's URLs, skipping anything that isn't http(s). */
  fun custom(urls: Collection<String>): List<FilterList> =
    urls
      .map { it.trim() }
      .filter { it.startsWith("https://", true) || it.startsWith("http://", true) }
      .distinct()
      .map { FilterList(customId(it), it.substringAfterLast('/').ifBlank { it }, it, true) }
}

/** Maps WebView requests to adblock-rust request types. */
object RequestTypes {
  private val SCRIPT = setOf("js", "mjs")
  private val STYLE = setOf("css")
  private val IMAGE = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp")
  private val FONT = setOf("woff", "woff2", "ttf", "otf", "eot")
  private val MEDIA = setOf("mp4", "webm", "m3u8", "mpd", "ts", "m4s", "mp3", "m4a", "aac", "ogg", "mkv")

  fun of(
    url: String,
    accept: String?,
    isMainFrame: Boolean,
    isXhr: Boolean = false,
  ): String {
    if (isMainFrame) return "document"
    if (isXhr) return "xmlhttprequest"
    when (extension(url)) {
      in SCRIPT -> return "script"
      in STYLE -> return "stylesheet"
      in IMAGE -> return "image"
      in FONT -> return "font"
      in MEDIA -> return "media"
    }
    val a = accept?.lowercase(Locale.ROOT).orEmpty()
    return when {
      a.startsWith("text/css") -> "stylesheet"
      a.startsWith("image/") -> "image"
      a.startsWith("video/") || a.startsWith("audio/") -> "media"
      a.startsWith("text/html") -> "subdocument"
      a.contains("javascript") -> "script"
      else -> "other"
    }
  }

  internal fun extension(url: String): String {
    val path = url.substringBefore('#').substringBefore('?')
    val last = path.substringAfter("://").substringAfter('/', "").substringAfterLast('/')
    val dot = last.lastIndexOf('.')
    return if (dot < 0) "" else last.substring(dot + 1).lowercase(Locale.ROOT)
  }
}

object SiteMatcher {
  fun host(url: String): String =
    url
      .substringAfter("://")
      .substringBefore('/')
      .substringBefore('?')
      .substringBefore('#')
      .substringAfterLast('@')
      .substringBefore(':')
      .lowercase(Locale.ROOT)
      .removePrefix("www.")

  /** True if [url]'s host is one of [sites] or a subdomain of one. */
  fun isAllowed(
    url: String,
    sites: Collection<String>,
  ): Boolean {
    if (sites.isEmpty()) return false
    val h = host(url)
    if (h.isEmpty()) return false
    return sites.any { raw ->
      val s = host(raw.trim())
      s.isNotEmpty() && (h == s || h.endsWith(".$s"))
    }
  }
}
