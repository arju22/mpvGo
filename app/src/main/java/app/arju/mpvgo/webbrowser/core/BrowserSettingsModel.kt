package app.arju.mpvgo.webbrowser.core

/** One row under the address bar while typing. */
data class UrlSuggestion(
  val title: String,
  val url: String,
  val kind: Kind,
) {
  enum class Kind { COPIED, BOOKMARK, HISTORY }
}

/** Address-bar suggestions: built only from the user's own bookmarks and history, never sent anywhere. */
object Suggestions {
  const val MAX = 6

  /** Copied link first, then bookmarks, then history; one row per URL. */
  fun merge(
    copied: String?,
    bookmarks: List<Pair<String, String>>,
    history: List<Pair<String, String>>,
    max: Int = MAX,
  ): List<UrlSuggestion> {
    val seen = HashSet<String>()
    val out = ArrayList<UrlSuggestion>()

    fun add(
      title: String,
      url: String,
      kind: UrlSuggestion.Kind,
    ) {
      if (out.size < max && url.isNotBlank() && seen.add(url.trimEnd('/'))) out += UrlSuggestion(title, url, kind)
    }
    copied?.let { add("", it, UrlSuggestion.Kind.COPIED) }
    bookmarks.forEach { (t, u) -> add(t, u, UrlSuggestion.Kind.BOOKMARK) }
    history.forEach { (t, u) -> add(t, u, UrlSuggestion.Kind.HISTORY) }
    return out
  }
}

object ClipboardLink {
  private val URL = Regex("^https?://\\S+$", RegexOption.IGNORE_CASE)

  /** The clipboard text when it is a single web link, else null. */
  fun extract(text: CharSequence?): String? {
    val t = text?.toString()?.trim() ?: return null
    return t.takeIf { it.length <= 2048 && URL.matches(it) }
  }
}

/** "Always use secure connections": tries https first and falls back to http per host. */
object HttpsUpgrade {
  private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

  /** The https version of [url], or null when it should load as is. */
  fun upgrade(
    url: String,
    httpOnlyHosts: Set<String>,
  ): String? {
    if (!url.startsWith("http://", ignoreCase = true)) return null
    val host = hostOf(url) ?: return null
    if (isLocal(host) || host in httpOnlyHosts) return null
    return "https://" + url.substring("http://".length)
  }

  fun hostOf(url: String): String? =
    url
      .substringAfter("://", "")
      .substringBefore('/')
      .substringBefore('?')
      .substringBefore('#')
      .substringAfterLast('@')
      .let { if (it.startsWith("[")) it.substringBefore(']') + "]" else it.substringBefore(':') }
      .lowercase()
      .takeIf { it.isNotEmpty() }

  /** Hosts that almost never have TLS: IP addresses, localhost and LAN names. */
  fun isLocal(host: String): Boolean =
    host == "localhost" ||
      host.startsWith("[") ||
      IPV4.matches(host) ||
      !host.contains('.') ||
      host.endsWith(".local") ||
      host.endsWith(".lan") ||
      host.endsWith(".home.arpa")

  /** The http version of an upgraded [url], for the fallback after an error. */
  fun downgrade(url: String): String = "http://" + url.substringAfter("://")
}

/** "Ask sites not to track me" request headers. */
object PrivacyHeaders {
  fun of(doNotTrack: Boolean): Map<String, String> = if (doNotTrack) mapOf("DNT" to "1", "Sec-GPC" to "1") else emptyMap()

  const val SCRIPT =
    "try{Object.defineProperty(Navigator.prototype,'doNotTrack',{get:function(){return '1'},configurable:true});" +
      "Object.defineProperty(Navigator.prototype,'globalPrivacyControl',{get:function(){return true},configurable:true});}catch(e){}"
}
