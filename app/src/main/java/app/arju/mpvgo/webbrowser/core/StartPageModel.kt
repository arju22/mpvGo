package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class SearchEngine(
  val label: String,
  private val url: String,
) {
  GOOGLE("Google", "https://www.google.com/search?q=%s"),
  DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
  BRAVE("Brave", "https://search.brave.com/search?q=%s"),
  BING("Bing", "https://www.bing.com/search?q=%s"),
  STARTPAGE("Startpage", "https://www.startpage.com/do/search?q=%s"),
  CUSTOM("Custom", ""),
  ;

  /** Search URL with a `%s` placeholder; a custom URL without `%s` falls back to Google. */
  fun template(custom: String): String =
    if (this == CUSTOM) {
      custom.trim().takeIf { it.contains("%s") && it.startsWith("http") } ?: GOOGLE.url
    } else {
      url
    }
}

enum class TopBarButton(val label: String) {
  BOOKMARKS("Bookmarks"),
  DOWNLOADS("Downloads"),
  HISTORY("History"),
  NEW_TAB("New tab"),
  SHARE("Share"),
  FIND("Find in page"),
  ;

  companion object {
    const val MAX = 2

    fun parse(raw: String): List<TopBarButton> =
      raw
        .split(',')
        .mapNotNull { name -> runCatching { valueOf(name.trim()) }.getOrNull() }
        .distinct()
        .take(MAX)
  }
}

@Serializable
data class SpeedDial(
  @SerialName("n") val name: String = "",
  @SerialName("u") val url: String = "",
)

object SpeedDials {
  const val MAX = 8

  val defaults =
    listOf(
      SpeedDial("Facebook", "https://m.facebook.com"),
      SpeedDial("X", "https://x.com"),
      SpeedDial("Instagram", "https://www.instagram.com"),
      SpeedDial("Reddit", "https://www.reddit.com"),
      SpeedDial("Vimeo", "https://vimeo.com"),
      SpeedDial("Dailymotion", "https://www.dailymotion.com"),
      SpeedDial("Archive.org", "https://archive.org/details/movies"),
    )

  private val json = Json { ignoreUnknownKeys = true }
  private val serializer = ListSerializer(SpeedDial.serializer())

  fun encode(list: List<SpeedDial>): String = json.encodeToString(serializer, list.take(MAX))

  /** Blank or unreadable input gives the defaults; an encoded empty list stays empty. */
  fun decode(raw: String): List<SpeedDial> {
    if (raw.isBlank()) return defaults
    return runCatching {
      json
        .decodeFromString(serializer, raw)
        .filter { it.url.isNotBlank() }
        .map { it.copy(name = it.name.ifBlank { SiteMatcher.host(it.url) }) }
        .take(MAX)
    }.getOrDefault(defaults)
  }
}
