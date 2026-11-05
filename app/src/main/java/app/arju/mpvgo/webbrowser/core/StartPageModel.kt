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

enum class TopBarButton(val label: String, val summary: String) {
  NET_SPEED("Network speed", "Live download and upload rate"),
  BOOKMARKS("Bookmarks", "Opens your bookmarks"),
  DOWNLOADS("Downloads", "Shows running count"),
  AD_BLOCK("Ad block", "Shows blocked count"),
  BOOKMARK_PAGE("Bookmark this page", "Star or edit the bookmark"),
  HOME("Home", "Back to the start page"),
  NEW_TAB("New tab", "Opens a start page"),
  DESKTOP("Desktop site", "Switch this tab's site version"),
  SHARE("Share", "Send the page link"),
  HISTORY("History", "Pages you visited"),
  FIND("Find in page", "Search the page text"),
  ;

  companion object {
    const val MAX = 2

    fun parse(raw: String): List<TopBarButton> =
      raw
        .split(',')
        .mapNotNull { name -> runCatching { valueOf(name.trim()) }.getOrNull() }
        .distinct()
        .take(MAX)

    fun encode(list: List<TopBarButton>): String = list.distinct().take(MAX).joinToString(",") { it.name }

    /** Tapping a button in the picker: removes it if shown, adds it when there is room. */
    fun toggle(
      current: List<TopBarButton>,
      button: TopBarButton,
    ): List<TopBarButton> =
      when {
        button in current -> current - button
        current.size < MAX -> current + button
        else -> current
      }

    /** Moves the shown button at [from] to [to]; out-of-range indexes change nothing. */
    fun move(
      current: List<TopBarButton>,
      from: Int,
      to: Int,
    ): List<TopBarButton> {
      if (from !in current.indices || to !in current.indices) return current
      return current.toMutableList().apply { add(to, removeAt(from)) }
    }
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
