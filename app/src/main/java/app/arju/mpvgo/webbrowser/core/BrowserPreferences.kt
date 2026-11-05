package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.core.preferences.preference.PreferenceStore

enum class VideoTapMode { ASK_ONCE_PER_SITE, ALWAYS_ASK, ALWAYS_MPVGO, ALWAYS_WEB }

enum class BrowserVideoPlayerMode(val label: String) {
  SMALL_WINDOW("Small window"),
  FULL_SCREEN("Full screen"),
}

enum class SiteChoice { MPVGO, WEB }

/** What "Clear data when I exit" can remove. */
enum class ExitClear(val label: String) {
  TABS("Open tabs"),
  HISTORY("History"),
  COOKIES("Cookies and site data"),
  CACHE("Cached images and files"),
}

class BrowserPreferences(store: PreferenceStore) {
  val videoTapMode = store.getString("browser_video_tap_mode", VideoTapMode.ASK_ONCE_PER_SITE.name)

  /** Entries are "host=MPVGO" or "host=WEB". */
  val siteChoices = store.getStringSet("browser_site_choices", emptySet())
  val floatingMediaButton = store.getBoolean("browser_media_button", true)
  val addWebVideosToRecents = store.getBoolean("browser_web_videos_in_recents", true)
  val stopAutoplay = store.getBoolean("browser_stop_autoplay", true)
  val openVideosIn = store.getString("browser_open_videos_in", BrowserVideoPlayerMode.SMALL_WINDOW.name)
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

  /** Reopen the last normal tabs when the browser starts. Private tabs are never saved. */
  val rememberTabs = store.getBoolean("browser_remember_tabs", true)

  /** History older than this many days is removed; 0 keeps everything. */
  val historyDays = store.getInt("browser_history_days", 90)

  // Appearance
  val siteTiles = store.getBoolean("browser_site_tiles", true)
  val textZoom = store.getInt("browser_text_zoom", 100)
  val darkWebsites = store.getBoolean("browser_dark_websites", false)

  // Top bar & gestures
  val showTabCount = store.getBoolean("browser_show_tab_count", true)
  val swipeAddressBar = store.getBoolean("browser_swipe_address_bar", true)
  val swipeTabsPanel = store.getBoolean("browser_swipe_tabs_panel", false)

  // Search
  val searchSuggestions = store.getBoolean("browser_search_suggestions", true)
  val suggestCopiedLink = store.getBoolean("browser_suggest_copied_link", true)

  // Privacy & security
  val blockThirdPartyCookies = store.getBoolean("browser_block_third_party_cookies", true)
  val httpsUpgrade = store.getBoolean("browser_https_upgrade", true)
  val safeBrowsing = store.getBoolean("browser_safe_browsing", true)
  val doNotTrack = store.getBoolean("browser_do_not_track", false)

  /** [ExitClear] names cleared when the user leaves the browser; empty means off. */
  val clearOnExit = store.getStringSet("browser_clear_on_exit", emptySet())

  /** Every browser preference, for "Reset browser settings". Speed dials and site lists stay. */
  val resettable
    get() =
      listOf(
        videoTapMode, floatingMediaButton, addWebVideosToRecents, stopAutoplay, openVideosIn, javascript, userAgent, desktopSite,
        searchEngine, customSearchUrl, topBarButtons, pullToRefresh, rememberTabs, historyDays,
        siteTiles, textZoom, darkWebsites, showTabCount, swipeAddressBar, swipeTabsPanel,
        searchSuggestions, suggestCopiedLink, blockThirdPartyCookies, httpsUpgrade, safeBrowsing,
        doNotTrack, clearOnExit,
      )

  fun exitClears(): Set<ExitClear> = clearOnExit.get().mapNotNull { runCatching { ExitClear.valueOf(it) }.getOrNull() }.toSet()

  fun engine(): SearchEngine = runCatching { SearchEngine.valueOf(searchEngine.get()) }.getOrDefault(SearchEngine.GOOGLE)

  fun searchTemplate(): String = engine().template(customSearchUrl.get())

  fun dials(): List<SpeedDial> = SpeedDials.decode(speedDials.get())

  fun saveDials(list: List<SpeedDial>) = speedDials.set(SpeedDials.encode(list))

  fun topButtons(): List<TopBarButton> = TopBarButton.parse(topBarButtons.get())

  fun mode(): VideoTapMode = runCatching { VideoTapMode.valueOf(videoTapMode.get()) }.getOrDefault(VideoTapMode.ASK_ONCE_PER_SITE)

  fun playerMode(): BrowserVideoPlayerMode =
    runCatching { BrowserVideoPlayerMode.valueOf(openVideosIn.get()) }.getOrDefault(BrowserVideoPlayerMode.SMALL_WINDOW)

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
    userGesture: Boolean = true,
  ): Action {
    if (!userGesture) return Action.LET_PAGE_PLAY
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

  /** Muted, looping, or tiny (<200px) preview videos without controls are ignored. */
  fun shouldIgnoreVideo(
    muted: Boolean,
    loop: Boolean,
    controls: Boolean,
    width: Int,
    height: Int,
  ): Boolean {
    if (controls) return false
    if (muted || loop) return true
    if ((width in 1..199) || (height in 1..199)) return true
    return false
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
