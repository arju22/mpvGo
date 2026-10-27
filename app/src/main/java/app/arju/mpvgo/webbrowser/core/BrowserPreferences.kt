package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.core.preferences.preference.PreferenceStore

enum class VideoTapMode { ASK_ONCE_PER_SITE, ALWAYS_ASK, ALWAYS_MPVGO, ALWAYS_WEB }

enum class SiteChoice { MPVGO, WEB }

class BrowserPreferences(store: PreferenceStore) {
  val videoTapMode = store.getString("browser_video_tap_mode", VideoTapMode.ASK_ONCE_PER_SITE.name)

  /** Entries are "host=MPVGO" or "host=WEB". */
  val siteChoices = store.getStringSet("browser_site_choices", emptySet())
  val floatingMediaButton = store.getBoolean("browser_media_button", true)
  val addWebVideosToRecents = store.getBoolean("browser_web_videos_in_recents", true)
  val javascript = store.getBoolean("browser_javascript", true)
  val userAgent = store.getString("browser_user_agent", "")
  val desktopSite = store.getBoolean("browser_desktop_site", false)
  val searchEngine = store.getString("browser_search_engine", SearchEngine.GOOGLE.name)
  val customSearchUrl = store.getString("browser_custom_search_url", "")

  /** Speed dials encoded by [SpeedDials.encode]; blank means the defaults. */
  val speedDials = store.getString("browser_speed_dials", "")

  /** Up to two top-bar shortcut buttons, comma-separated [TopBarButton] names. */
  val topBarButtons = store.getString("browser_top_bar_buttons", "${TopBarButton.BOOKMARKS.name},${TopBarButton.DOWNLOADS.name}")
  val pullToRefresh = store.getBoolean("browser_pull_to_refresh", true)

  fun engine(): SearchEngine = runCatching { SearchEngine.valueOf(searchEngine.get()) }.getOrDefault(SearchEngine.GOOGLE)

  fun searchTemplate(): String = engine().template(customSearchUrl.get())

  fun dials(): List<SpeedDial> = SpeedDials.decode(speedDials.get())

  fun saveDials(list: List<SpeedDial>) = speedDials.set(SpeedDials.encode(list))

  fun topButtons(): List<TopBarButton> = TopBarButton.parse(topBarButtons.get())

  fun mode(): VideoTapMode = runCatching { VideoTapMode.valueOf(videoTapMode.get()) }.getOrDefault(VideoTapMode.ASK_ONCE_PER_SITE)

  fun siteChoice(host: String): SiteChoice? = PlayTakeover.choiceFor(host, siteChoices.get())

  fun rememberSite(
    host: String,
    choice: SiteChoice?,
  ) {
    siteChoices.set(PlayTakeover.withChoice(siteChoices.get(), host, choice))
  }
}

/** What happens when a page starts playing a video. */
object PlayTakeover {
  enum class Action { ASK, PLAY_IN_MPVGO, LET_PAGE_PLAY }

  fun decide(
    mode: VideoTapMode,
    siteChoice: SiteChoice?,
    pageUrl: String,
    hasStream: Boolean,
    isDrm: Boolean,
  ): Action {
    if (isDrm || MediaClassifier.isBlockedForPlayback(pageUrl)) return Action.LET_PAGE_PLAY
    return when (mode) {
      VideoTapMode.ALWAYS_WEB -> Action.LET_PAGE_PLAY
      VideoTapMode.ALWAYS_ASK -> Action.ASK
      VideoTapMode.ALWAYS_MPVGO -> if (hasStream) Action.PLAY_IN_MPVGO else Action.ASK
      VideoTapMode.ASK_ONCE_PER_SITE ->
        when (siteChoice) {
          SiteChoice.WEB -> Action.LET_PAGE_PLAY
          SiteChoice.MPVGO -> if (hasStream) Action.PLAY_IN_MPVGO else Action.ASK
          null -> Action.ASK
        }
    }
  }

  fun choiceFor(
    host: String,
    entries: Set<String>,
  ): SiteChoice? {
    val prefix = "${host.lowercase()}="
    val value = entries.firstOrNull { it.startsWith(prefix) }?.substringAfter('=') ?: return null
    return runCatching { SiteChoice.valueOf(value) }.getOrNull()
  }

  fun withChoice(
    entries: Set<String>,
    host: String,
    choice: SiteChoice?,
  ): Set<String> {
    val h = host.lowercase()
    val rest = entries.filterNot { it.startsWith("$h=") }.toSet()
    return if (choice == null) rest else rest + "$h=${choice.name}"
  }
}
