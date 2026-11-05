package app.arju.mpvgo.webbrowser.settings

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.webkit.WebViewCompat
import app.arju.mpvgo.core.preferences.preference.collectAsState
import app.arju.mpvgo.core.settings.PreferenceDivider
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.downloader.data.DownloadPreferences
import app.arju.mpvgo.downloader.ui.DownloadPreferencesScreen
import app.arju.mpvgo.webbrowser.BrowserLinks
import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import app.arju.mpvgo.webbrowser.core.BrowserPreferences
import app.arju.mpvgo.webbrowser.core.BrowserVideoPlayerMode
import app.arju.mpvgo.webbrowser.core.ExitClear
import app.arju.mpvgo.webbrowser.core.SearchEngine
import app.arju.mpvgo.webbrowser.core.TabManager
import app.arju.mpvgo.webbrowser.core.TopBarButton
import app.arju.mpvgo.webbrowser.core.VideoTapMode
import app.arju.mpvgo.webbrowser.library.BrowserLibrary
import app.arju.mpvgo.webbrowser.ui.ClearDataDialog
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.ListPreference
import me.zhanghai.compose.preference.SliderPreference
import org.koin.compose.koinInject
import kotlin.math.roundToInt

/** Version of the adblock-rust crate in native/adblock-jni/Cargo.toml. */
internal const val ADBLOCK_RUST_VERSION = "0.13.3"

internal val VideoTapMode.label: String
  get() =
    when (this) {
      VideoTapMode.ASK_ONCE_PER_SITE -> "Ask once per site"
      VideoTapMode.ALWAYS_ASK -> "Always ask"
      VideoTapMode.ALWAYS_MPVGO -> "Always play in mpvGo"
      VideoTapMode.ALWAYS_WEB -> "Always use the web player"
    }

internal fun historyLabel(days: Int): String = if (days <= 0) "Forever" else if (days == 365) "1 year" else "$days days"

internal fun updatedLabel(at: Long): String =
  if (at <= 0L) {
    "not downloaded yet"
  } else {
    "updated " + DateUtils.getRelativeTimeSpanString(at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString().lowercase()
  }

@Composable
internal fun AppearancePage() {
  val tabs = koinInject<TabManager>()
  val prefs = tabs.prefs
  BrowserSettingsScaffold("Appearance") {
    section("Start page") {
      SwitchRow(prefs.siteTiles, "Site tiles", "Shortcuts below the search box")
    }
    section("Pages") {
      val zoom by prefs.textZoom.collectAsState()
      var sliding by remember(zoom) { mutableFloatStateOf(zoom.toFloat()) }
      SliderPreference(
        value = zoom.toFloat(),
        onValueChange = {
          prefs.textZoom.set(it.roundToInt())
          tabs.applySettings()
        },
        sliderValue = sliding,
        onSliderValueChange = { sliding = (it / 10f).roundToInt() * 10f },
        title = { Text("Text size") },
        valueRange = 50f..200f,
        valueSteps = 14,
        summary = { Summary("${sliding.roundToInt()}%") },
      )
      PreferenceDivider()
      SwitchRow(prefs.darkWebsites, "Dark websites", "Darken pages when mpvGo uses a dark theme") { tabs.applySettings() }
      PreferenceDivider()
      SwitchRow(prefs.desktopSite, "Desktop site by default", "New tabs ask for the desktop version")
    }
  }
}

@Composable
internal fun TopBarPage() {
  val backstack = LocalBackStack.current
  val prefs = koinInject<BrowserPreferences>()
  val buttons by prefs.topBarButtons.collectAsState()
  BrowserSettingsScaffold("Top bar & gestures") {
    section("Top bar") {
      LinkRow(
        "Top bar buttons",
        TopBarButton.parse(buttons).joinToString(", ") { it.label }.ifEmpty { "None" },
      ) { backstack.add(BrowserSettingsSubPage(BrowserSettingsSub.TOP_BAR_BUTTONS)) }
      PreferenceDivider()
      SwitchRow(prefs.showTabCount, "Show tab count", "Number on the Tabs button")
    }
    section("Gestures") {
      SwitchRow(prefs.pullToRefresh, "Pull down to refresh", "Swipe down at the top of a page")
      PreferenceDivider()
      SwitchRow(prefs.swipeAddressBar, "Swipe address bar to switch tabs", "Left or right on the address")
      PreferenceDivider()
      SwitchRow(prefs.swipeTabsPanel, "Swipe to open tabs panel", "From the left edge of the page")
    }
    section("Media button") {
      SwitchRow(prefs.floatingMediaButton, "Floating media button", "Show when videos are found")
    }
    footnote("The phone's edge-swipe Back is never used by the browser.")
  }
}

@Composable
internal fun SearchPage() {
  val prefs = koinInject<BrowserPreferences>()
  val engineName by prefs.searchEngine.collectAsState()
  val custom by prefs.customSearchUrl.collectAsState()
  val engine = runCatching { SearchEngine.valueOf(engineName) }.getOrDefault(SearchEngine.GOOGLE)
  var askCustom by remember { mutableStateOf(false) }
  BrowserSettingsScaffold("Search") {
    section(null) {
      ListPreference(
        value = engine,
        onValueChange = { if (it == SearchEngine.CUSTOM) askCustom = true else prefs.searchEngine.set(it.name) },
        values = SearchEngine.entries,
        title = { Text("Search engine") },
        summary = { Summary(if (engine == SearchEngine.CUSTOM) custom else engine.label) },
        valueToText = { AnnotatedString(it.label) },
      )
      PreferenceDivider()
      SwitchRow(prefs.searchSuggestions, "Search suggestions", "From your history and bookmarks, on this phone only")
      PreferenceDivider()
      SwitchRow(prefs.suggestCopiedLink, "Suggest copied link", "When you tap the address bar")
    }
  }
  if (askCustom) {
    TextInputDialog(
      title = "Custom search engine",
      initial = custom.ifBlank { "https://" },
      label = "URL with %s for the search words",
      onDismiss = { askCustom = false },
      valid = { it.startsWith("http") && "%s" in it },
    ) {
      prefs.customSearchUrl.set(it)
      prefs.searchEngine.set(SearchEngine.CUSTOM.name)
      askCustom = false
    }
  }
}

@Composable
internal fun TabsPage() {
  val context = LocalContext.current
  val prefs = koinInject<BrowserPreferences>()
  var linksOn by remember { mutableStateOf(BrowserLinks.isEnabled(context)) }
  BrowserSettingsScaffold("Tabs") {
    section(null) {
      SwitchRow(prefs.rememberTabs, "Remember tabs", "Reopen where I left off")
      PreferenceDivider()
      me.zhanghai.compose.preference.SwitchPreference(
        value = linksOn,
        onValueChange = {
          BrowserLinks.setEnabled(context, it)
          linksOn = it
        },
        title = { Text("Open links from other apps") },
        summary = { Summary("Make mpvGo a browser choice") },
      )
    }
    footnote("Private tabs are never remembered.")
  }
}

@Composable
internal fun VideoPage() {
  val backstack = LocalBackStack.current
  val prefs = koinInject<BrowserPreferences>()
  val mode by prefs.videoTapMode.collectAsState()
  val choices by prefs.siteChoices.collectAsState()
  val current = runCatching { VideoTapMode.valueOf(mode) }.getOrDefault(VideoTapMode.ASK_ONCE_PER_SITE)
  val openVideosInStr by prefs.openVideosIn.collectAsState()
  val playerMode = runCatching { BrowserVideoPlayerMode.valueOf(openVideosInStr) }.getOrDefault(BrowserVideoPlayerMode.SMALL_WINDOW)
  BrowserSettingsScaffold("Video playback") {
    section(null) {
      ListPreference(
        value = current,
        onValueChange = { prefs.videoTapMode.set(it.name) },
        values = VideoTapMode.entries,
        title = { Text("When I tap a video") },
        summary = { Summary(current.label) },
        valueToText = { AnnotatedString(it.label) },
      )
      PreferenceDivider()
      ListPreference(
        value = playerMode,
        onValueChange = { prefs.openVideosIn.set(it.name) },
        values = BrowserVideoPlayerMode.entries,
        title = { Text("Open browser videos in") },
        summary = { Summary(playerMode.label) },
        valueToText = { AnnotatedString(it.label) },
      )
      PreferenceDivider()
      LinkRow("Site choices", if (choices.size == 1) "1 site" else "${choices.size} sites") {
        backstack.add(BrowserSettingsSubPage(BrowserSettingsSub.SITE_CHOICES))
      }
      PreferenceDivider()
      SwitchRow(prefs.addWebVideosToRecents, "Add web videos to Recents", "Not for private tabs")
      PreferenceDivider()
      SwitchRow(prefs.stopAutoplay, "Stop autoplay on sites", "Pause autoplay videos until tapped")
    }
  }
}

@Composable
internal fun DownloadsPage() {
  val backstack = LocalBackStack.current
  val downloads = koinInject<DownloadPreferences>()
  BrowserSettingsScaffold("Downloads") {
    section(null) {
      SwitchRow(downloads.askBeforeEveryDownload, "Ask before every download", "Check the name and folder first")
      PreferenceDivider()
      LinkRow("Download settings", "Folder, connections, Wi-Fi only") { backstack.add(DownloadPreferencesScreen) }
    }
  }
}

@Composable
internal fun AdBlockPage() {
  val backstack = LocalBackStack.current
  val adPrefs = koinInject<AdBlockPreferences>()
  val adBlocker = koinInject<AdBlocker>()
  val on by adPrefs.enabled.collectAsState()
  val blocked by adBlocker.blockedTotal.collectAsState()
  val lists by adPrefs.enabledLists.collectAsState()
  val custom by adPrefs.customListUrls.collectAsState()
  val updated by adPrefs.lastUpdated.collectAsState()
  val filters by adPrefs.myFilters.collectAsState()
  val allowed by adPrefs.allowedSites.collectAsState()
  val rules = filters.lines().count { it.isNotBlank() && !it.trimStart().startsWith("!") }
  BrowserSettingsScaffold("Ad block") {
    section(null) {
      SwitchRow(adPrefs.enabled, "Block ads and trackers", "$blocked blocked so far")
      PreferenceDivider()
      SwitchRow(adPrefs.blockPopups, "Block pop-ups and redirects", enabled = on)
      PreferenceDivider()
      SwitchRow(adPrefs.cosmetic, "Hide ad spaces", "Removes empty boxes left by blocked ads", enabled = on)
    }
    section("Filters") {
      LinkRow("Filter lists", "${listCount(lists, custom)} on · ${updatedLabel(updated)}") {
        backstack.add(BrowserSettingsSubPage(BrowserSettingsSub.FILTER_LISTS))
      }
      PreferenceDivider()
      LinkRow("My filters", if (rules == 1) "1 rule" else "$rules rules") {
        backstack.add(BrowserSettingsSubPage(BrowserSettingsSub.MY_FILTERS))
      }
      PreferenceDivider()
      LinkRow("Allowed sites", if (allowed.size == 1) "1 site" else "${allowed.size} sites") {
        backstack.add(BrowserSettingsSubPage(BrowserSettingsSub.ALLOWED_SITES))
      }
    }
  }
}

@Composable
internal fun PrivacyPage() {
  val context = LocalContext.current
  val backstack = LocalBackStack.current
  val scope = rememberCoroutineScope()
  val tabs = koinInject<TabManager>()
  val library = koinInject<BrowserLibrary>()
  val adPrefs = koinInject<AdBlockPreferences>()
  val prefs = tabs.prefs
  val exit by prefs.clearOnExit.collectAsState()
  val days by prefs.historyDays.collectAsState()
  var clearing by remember { mutableStateOf(false) }
  var pickExit by remember { mutableStateOf(false) }
  BrowserSettingsScaffold("Privacy & security") {
    section("Protection") {
      SwitchRow(prefs.blockThirdPartyCookies, "Block third-party cookies", "Stops most cross-site tracking") { tabs.applySettings() }
      PreferenceDivider()
      SwitchRow(prefs.httpsUpgrade, "Always use secure connections", "Try HTTPS first; local addresses stay as typed")
      PreferenceDivider()
      SwitchRow(prefs.safeBrowsing, "Safe Browsing", "Warn about dangerous sites") { tabs.applySettings() }
      PreferenceDivider()
      SwitchRow(prefs.doNotTrack, "Ask sites not to track me", "Sends Do Not Track and Global Privacy Control") { tabs.applySettings() }
      PreferenceDivider()
      LinkRow("Site permissions", "Camera, microphone, location") {
        backstack.add(BrowserSettingsSubPage(BrowserSettingsSub.SITE_PERMISSIONS))
      }
    }
    section("Your data") {
      LinkRow("Clear browsing data", "History, cookies, cache") { clearing = true }
      PreferenceDivider()
      val picked = ExitClear.entries.filter { it.name in exit }
      LinkRow("Clear data when I exit", if (picked.isEmpty()) "Off" else picked.joinToString(", ") { it.label }) { pickExit = true }
      PreferenceDivider()
      ListPreference(
        value = days,
        onValueChange = {
          prefs.historyDays.set(it)
          library.pruneNow()
        },
        values = listOf(30, 90, 365, 0),
        title = { Text("Keep history") },
        summary = { Summary(historyLabel(days)) },
        valueToText = { AnnotatedString(historyLabel(it)) },
      )
    }
  }
  if (clearing) {
    ClearDataDialog(
      onClear = { range, historyToo, request ->
        if (historyToo) scope.launch { library.clearHistory(range.since(System.currentTimeMillis())) }
        tabs.clearBrowsingData(request.cookies, request.cache)
        if (request.siteSettings) {
          prefs.siteChoices.set(emptySet())
          adPrefs.allowedSites.set(emptySet())
        }
        clearing = false
        Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
      },
      onDismiss = { clearing = false },
    )
  }
  if (pickExit) {
    ExitClearDialog(
      initial = ExitClear.entries.filter { it.name in exit }.toSet(),
      onDismiss = { pickExit = false },
    ) { chosen ->
      prefs.clearOnExit.set(chosen.map { it.name }.toSet())
      pickExit = false
    }
  }
}

@Composable
internal fun AdvancedPage() {
  val context = LocalContext.current
  val tabs = koinInject<TabManager>()
  val prefs = tabs.prefs
  val ua by prefs.userAgent.collectAsState()
  var editUa by remember { mutableStateOf(false) }
  var confirmReset by remember { mutableStateOf(false) }
  val webView = remember { WebViewCompat.getCurrentWebViewPackage(context)?.versionName ?: "unknown" }
  BrowserSettingsScaffold("Advanced") {
    section(null) {
      LinkRow("User agent", ua.ifBlank { "Default (Android)" }) { editUa = true }
      PreferenceDivider()
      SwitchRow(prefs.javascript, "JavaScript", "Needed for video detection") { tabs.applySettings() }
      PreferenceDivider()
      LinkRow("Reset browser settings", "Bookmarks, history, tiles and site lists stay") { confirmReset = true }
      PreferenceDivider()
      LinkRow("Versions", "WebView $webView · Ad block $ADBLOCK_RUST_VERSION") {}
    }
  }
  if (editUa) {
    UserAgentDialog(
      initial = ua,
      onDismiss = { editUa = false },
    ) {
      prefs.userAgent.set(it)
      tabs.applySettings()
      editUa = false
    }
  }
  if (confirmReset) {
    ConfirmDialog(
      title = "Reset browser settings?",
      text = "All browser settings go back to their defaults. Bookmarks, history, start page tiles and site lists are kept.",
      confirm = "Reset",
      onDismiss = { confirmReset = false },
    ) {
      prefs.resettable.forEach { it.delete() }
      tabs.applySettings()
      confirmReset = false
      Toast.makeText(context, "Browser settings reset", Toast.LENGTH_SHORT).show()
    }
  }
}
