package app.arju.mpvgo.webbrowser.settings

/** The nine Browser settings pages, in the order the main page shows them. */
enum class BrowserSettingsCategory(
  val title: String,
  val summary: String,
  val group: String,
) {
  APPEARANCE("Appearance", "Start page, text size, dark websites", "Look & layout"),
  TOP_BAR("Top bar & gestures", "Top bar buttons, pull to refresh, swipes", "Look & layout"),
  SEARCH("Search", "Engine, suggestions", "Browsing"),
  TABS("Tabs", "Remember tabs, open links from apps", "Browsing"),
  VIDEO("Video playback", "When I tap a video, site choices", "Video & downloads"),
  DOWNLOADS("Downloads", "Ask before download, download settings", "Video & downloads"),
  AD_BLOCK("Ad block", "Filter lists, my filters, allowed sites", "Ad block & privacy"),
  PRIVACY("Privacy & security", "Cookies, HTTPS, clear data", "Ad block & privacy"),
  ADVANCED("Advanced", "User agent, JavaScript, reset, versions", "Advanced"),
}

data class BrowserSettingEntry(
  val title: String,
  val category: BrowserSettingsCategory,
  val keywords: List<String> = emptyList(),
)

/** Everything the Browser settings search box can find. */
object BrowserSettingsIndex {
  val entries: List<BrowserSettingEntry> =
    buildList {
      fun add(
        category: BrowserSettingsCategory,
        title: String,
        vararg keywords: String,
      ) = add(BrowserSettingEntry(title, category, keywords.toList()))

      BrowserSettingsCategory.APPEARANCE.let {
        add(it, "Site tiles", "start page", "speed dial", "shortcuts")
        add(it, "Text size", "font", "zoom")
        add(it, "Dark websites", "dark mode", "night", "theme")
        add(it, "Desktop site by default", "desktop", "user agent")
      }
      BrowserSettingsCategory.TOP_BAR.let {
        add(it, "Top bar buttons", "toolbar", "icons", "shortcut")
        add(it, "Show tab count", "tabs", "number")
        add(it, "Pull down to refresh", "reload", "swipe")
        add(it, "Swipe address bar to switch tabs", "gesture", "swipe")
        add(it, "Swipe to open tabs panel", "gesture", "drawer")
        add(it, "Floating media button", "video", "fab")
      }
      BrowserSettingsCategory.SEARCH.let {
        add(it, "Search engine", "google", "duckduckgo", "bing", "brave", "startpage")
        add(it, "Search suggestions", "autocomplete", "history")
        add(it, "Suggest copied link", "clipboard", "paste")
      }
      BrowserSettingsCategory.TABS.let {
        add(it, "Remember tabs", "restore", "session")
        add(it, "Open links from other apps", "default browser", "intent")
      }
      BrowserSettingsCategory.VIDEO.let {
        add(it, "When I tap a video", "play", "mpvgo", "web player")
        add(it, "Site choices", "sites", "video")
        add(it, "Add web videos to Recents", "recent", "history")
      }
      BrowserSettingsCategory.DOWNLOADS.let {
        add(it, "Ask before every download", "confirm")
        add(it, "Download settings", "folder", "connections", "wifi")
      }
      BrowserSettingsCategory.AD_BLOCK.let {
        add(it, "Block ads and trackers", "adblock")
        add(it, "Block pop-ups and redirects", "popup")
        add(it, "Filter lists", "easylist", "update")
        add(it, "My filters", "custom rules")
        add(it, "Allowed sites", "whitelist", "exceptions")
      }
      BrowserSettingsCategory.PRIVACY.let {
        add(it, "Block third-party cookies", "cookies", "tracking")
        add(it, "Always use secure connections", "https", "http")
        add(it, "Safe Browsing", "malware", "phishing")
        add(it, "Ask sites not to track me", "do not track", "dnt", "gpc")
        add(it, "Site permissions", "camera", "microphone", "location")
        add(it, "Clear browsing data", "history", "cookies", "cache")
        add(it, "Clear data when I exit", "exit", "close")
        add(it, "Keep history", "history", "days")
      }
      BrowserSettingsCategory.ADVANCED.let {
        add(it, "User agent", "ua", "browser identity")
        add(it, "JavaScript", "js", "scripts")
        add(it, "Reset browser settings", "default", "restore")
        add(it, "Versions", "webview", "about")
      }
    }

  /** Entries whose title, page or keywords contain every word of [query]. */
  fun search(query: String): List<BrowserSettingEntry> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return entries.filter { e ->
      val hay = (listOf(e.title, e.category.title) + e.keywords).joinToString(" ").lowercase()
      words.all { it in hay }
    }
  }
}
