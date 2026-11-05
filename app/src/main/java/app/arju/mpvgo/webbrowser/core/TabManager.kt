package app.arju.mpvgo.webbrowser.core

import android.content.ActivityNotFoundException
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import app.arju.mpvgo.downloader.ui.DownloadDraft
import app.arju.mpvgo.player.MediaRequest
import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import app.arju.mpvgo.webbrowser.library.BrowserLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object TabNavigation {
  /** Returns the id of the tab after moving [direction] (+1 or -1) within [siblings] of [currentId], or null if at boundary. */
  fun stepId(currentId: Long?, siblings: List<Long>, direction: Int): Long? {
    if (currentId == null || siblings.isEmpty()) return null
    val i = siblings.indexOf(currentId)
    if (i < 0) return null
    return siblings.getOrNull(i + direction)
  }
}

object BrowserBackAction {
  enum class Action {
    CLOSE_DRAWER,
    CLOSE_LIBRARY,
    CLOSE_MEDIA,
    CLOSE_SITE,
    CLOSE_FIND,
    CLOSE_FULLSCREEN,
    WEBVIEW_GO_BACK,
    CLOSE_TAB_TO_OPENER,
    EXIT_BROWSER,
  }

  fun decide(
    drawerOpen: Boolean,
    libraryOpen: Boolean,
    mediaOpen: Boolean,
    siteOpen: Boolean,
    findOpen: Boolean,
    fullscreenOpen: Boolean,
    canGoBack: Boolean,
    hasValidOpener: Boolean,
  ): Action =
    when {
      drawerOpen -> Action.CLOSE_DRAWER
      libraryOpen -> Action.CLOSE_LIBRARY
      mediaOpen -> Action.CLOSE_MEDIA
      siteOpen -> Action.CLOSE_SITE
      findOpen -> Action.CLOSE_FIND
      fullscreenOpen -> Action.CLOSE_FULLSCREEN
      canGoBack -> Action.WEBVIEW_GO_BACK
      hasValidOpener -> Action.CLOSE_TAB_TO_OPENER
      else -> Action.EXIT_BROWSER
    }
}

/**
 * Owns all browser tabs and their WebViews. Lives as an app singleton, so tabs survive leaving
 * the browser screen; WebViews are re-parented into whichever Activity shows them.
 */
class TabManager(
  private val appContext: Context,
  private val adBlocker: AdBlocker,
  adPrefs: AdBlockPreferences,
  val prefs: BrowserPreferences,
  private val library: BrowserLibrary,
) : TabHost {
  sealed interface Event {
    /** The page started a video and the user should pick mpvGo or the web player. */
    data class Ask(val tabId: Long, val host: String, val media: DetectedMedia?) : Event

    data class Launch(val request: MediaRequest) : Event

    /** DRM or a site that can't be played outside the page. */
    data class ProtectedVideo(val tabId: Long) : Event

    data class PopupBlocked(val tabId: Long) : Event

    data class Fullscreen(val view: View?, val callback: WebChromeClient.CustomViewCallback?) : Event

    /** The page started a file download. */
    data class Download(val tabId: Long, val draft: DownloadDraft) : Event

    /** Long-press on a link: [media] is set when the link itself is a video. */
    data class LinkMenu(val tabId: Long, val url: String, val media: DetectedMedia?) : Event
  }

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val factory = GoWebViewFactory(appContext, adBlocker, adPrefs, prefs, scope)
  private val http by lazy {
    OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
  }

  private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
  val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

  private val _activeId = MutableStateFlow<Long?>(null)
  val activeId: StateFlow<Long?> = _activeId.asStateFlow()

  private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 8)
  val events: SharedFlow<Event> = _events.asSharedFlow()

  private val context = MutableContextWrapper(appContext)
  private var nextId = 1L

  /** Replies of tabs whose page is paused, waiting for the user's Ask answer. */
  private val pendingReplies = HashMap<Long, JavaScriptReplyProxy>()

  val active: BrowserTab? get() = _tabs.value.firstOrNull { it.id == _activeId.value }

  init {
    appContext.registerComponentCallbacks(
      object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) = this@TabManager.onTrimMemory(level)

        override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() = onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
      },
    )
  }

  fun tab(id: Long): BrowserTab? = _tabs.value.firstOrNull { it.id == id }

  /** Call from the Activity that shows the browser, so dialogs and <select> popups work. */
  fun attach(activity: Context) {
    context.baseContext = activity
    adBlocker.warmUp()
  }

  fun detach(activity: Context) {
    if (context.baseContext === activity) context.baseContext = appContext
  }

  fun newTab(
    url: String? = null,
    private: Boolean = false,
    select: Boolean = true,
    openerId: Long? = null,
  ): BrowserTab {
    val tab = BrowserTab(nextId++, private, url, scope, ::fetchText)
    tab.openerId = openerId
    tab._desktop.value = prefs.desktopSite.get()
    _tabs.value = _tabs.value + tab
    if (select) select(tab.id)
    if (!url.isNullOrBlank()) load(tab, url)
    saveSession()
    return tab
  }

  /**
   * Reopens the tabs saved by "Remember tabs". Only the active tab gets a WebView now; the
   * others load when selected. Returns false when there was nothing to restore.
   */
  fun restoreSession(): Boolean {
    if (_tabs.value.isNotEmpty() || !prefs.rememberTabs.get()) return false
    val session = TabSession.read(appContext.filesDir) ?: return false
    if (session.tabs.isEmpty()) return false
    val restored =
      session.tabs.map { saved ->
        BrowserTab(nextId++, false, saved.url, scope, ::fetchText).also {
          it._title.value = saved.title
          it._desktop.value = saved.desktop
          it.pendingScrollY = saved.scrollY
          it.lastActive = 0
        }
      }
    _tabs.value = restored
    select(restored[session.active].id)
    return true
  }

  private var saveJob: Job? = null

  /** Writes the normal tabs to disk shortly after the last change. */
  fun saveSession() {
    saveJob?.cancel()
    saveJob =
      scope.launch {
        delay(SAVE_DELAY)
        writeSession()
      }
  }

  private suspend fun writeSession() {
    if (!prefs.rememberTabs.get()) {
      withContext(Dispatchers.IO) { TabSession.file(appContext.filesDir).delete() }
      return
    }
    val normal = _tabs.value.filterNot { it.isPrivate }
    val session =
      SavedSession(
        tabs =
          normal.map {
            SavedTab(
              url = it.url.value,
              title = it.title.value,
              desktop = it.desktop.value,
              scrollY = it.webView?.scrollY ?: it.pendingScrollY,
            )
          },
        active =
          normal.indexOfFirst { it.id == _activeId.value }.takeIf { it >= 0 }
            ?: normal.indices.maxByOrNull { normal[it].lastActive } ?: 0,
      )
    withContext(Dispatchers.IO) { TabSession.write(appContext.filesDir, session) }
  }

  /**
   * Clear browsing data: [cookies] signs out of sites in normal tabs, [cache] drops cached
   * files. Private tabs keep their own separate profile.
   */
  fun clearBrowsingData(
    cookies: Boolean,
    cache: Boolean,
  ) {
    if (cookies) {
      runCatching {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        WebStorage.getInstance().deleteAllData()
      }
    }
    if (cache) {
      val view = _tabs.value.firstNotNullOfOrNull { it.webView }
      if (view != null) {
        view.clearCache(true)
      } else {
        runCatching { WebView(context).apply { clearCache(true) }.destroy() }
      }
    }
  }

  fun select(id: Long) {
    val previous = active
    if (previous != null && previous.id != id) pause(previous)
    _activeId.value = id
    tab(id)?.let {
      it.lastActive = System.currentTimeMillis()
      it.webView?.onResume()
    }
    saveSession()
  }

  fun close(id: Long) {
    val tab = tab(id) ?: return
    val remaining = _tabs.value - tab
    destroyWebView(tab)
    pendingReplies.remove(id)
    _tabs.value = remaining
    if (_activeId.value == id) {
      _activeId.value = (remaining.firstOrNull { it.id == tab.openerId } ?: remaining.maxByOrNull { it.lastActive })?.id
    }
    if (tab.isPrivate && remaining.none { it.isPrivate }) BrowserProfiles.clearPrivate()
    saveSession()
  }

  fun closeAll(private: Boolean) {
    _tabs.value.filter { it.isPrivate == private }.forEach { close(it.id) }
  }

  /** The tab's WebView, created or restored from hibernation on demand. */
  fun webViewFor(tab: BrowserTab): WebView {
    tab.webView?.let { return it }
    val view = factory.create(context, tab, this)
    tab.webView = view
    tab._hibernated.value = false
    val saved = tab.savedState
    tab.savedState = null
    if (saved != null && view.restoreState(saved) != null) {
      tab._canGoBack.value = view.canGoBack()
      tab._canGoForward.value = view.canGoForward()
      return view
    }
    tab.url.value.takeIf { it.isNotBlank() }?.let { factory.load(view, it) }
    return view
  }

  /** Re-applies changed browser settings to every open WebView. */
  fun applySettings() {
    _tabs.value.forEach { tab -> tab.webView?.let { factory.applySettings(it, tab) } }
  }

  /**
   * Leaving the browser (Close browser, Exit, or Back on the first page): runs "Clear data
   * when I exit". Returns true when the open tabs were closed.
   */
  fun onExitBrowser(): Boolean {
    val what = prefs.exitClears()
    if (what.isEmpty()) return false
    if (ExitClear.HISTORY in what) scope.launch { library.clearHistory(0) }
    if (ExitClear.COOKIES in what || ExitClear.CACHE in what) {
      clearBrowsingData(ExitClear.COOKIES in what, ExitClear.CACHE in what)
    }
    if (ExitClear.TABS in what) {
      _tabs.value.forEach { close(it.id) }
      return true
    }
    return false
  }

  fun load(
    tab: BrowserTab,
    input: String,
  ) {
    val url = UrlInput.toUrl(input, prefs.searchTemplate())
    if (url != tab.pageUrl) {
      tab.sniffer.reset()
      tab._blocked.value = 0
    }
    tab.pageUrl = url
    tab._url.value = url
    factory.load(webViewFor(tab), url)
  }

  /** Shows the start page in [tab]; the page history stays, so Back returns to it. */
  fun goHome(tab: BrowserTab) {
    tab.webView?.let {
      it.stopLoading()
      pause(tab)
    }
    tab.sniffer.reset()
    tab._loading.value = false
    tab._title.value = ""
    tab._url.value = ""
    tab.pageUrl = ""
  }

  fun setDesktop(
    tab: BrowserTab,
    on: Boolean,
  ) {
    tab._desktop.value = on
    val view = tab.webView ?: return
    val mobile = tab.mobileUserAgent.ifBlank { view.settings.userAgentString }
    view.settings.userAgentString = if (on) DesktopMode.userAgent(mobile) else mobile
    tab.userAgent = view.settings.userAgentString
    if (!tab.isStartPage) view.reload()
  }

  /** Tabs of the same kind (normal or private) as [tab], in opening order. */
  fun siblings(tab: BrowserTab): List<BrowserTab> = _tabs.value.filter { it.isPrivate == tab.isPrivate }

  /** Selects the next (+1) or previous (-1) tab of the same kind; returns true if switched, false if at boundary. */
  fun step(direction: Int): Boolean = step(active ?: return false, direction)

  /** Selects the next (+1) or previous (-1) tab of the same kind; no wrap-around. */
  fun step(
    tab: BrowserTab,
    direction: Int,
  ): Boolean {
    val list = siblings(tab)
    val nextId = TabNavigation.stepId(tab.id, list.map { it.id }, direction) ?: return false
    select(nextId)
    return true
  }

  fun pauseAll() {
    _tabs.value.forEach(::pause)
    saveSession()
  }

  fun resumeActive() {
    active?.webView?.onResume()
  }

  private fun pause(tab: BrowserTab) {
    val view = tab.webView ?: return
    runCatching { view.evaluateJavascript("window.__mpvgo&&window.__mpvgo.pauseAll()", null) }
    view.onPause()
  }

  /** Low memory: save and destroy WebViews of background tabs, keeping the active one. */
  @Suppress("DEPRECATION")
  fun onTrimMemory(level: Int) {
    if (level < ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW || level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) return
    val keep = if (level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) null else _activeId.value
    _tabs.value.filter { it.id != keep && it.webView != null }.forEach(::hibernate)
  }

  private fun hibernate(tab: BrowserTab) {
    val view = tab.webView ?: return
    if (!tab.isPrivate) {
      val state = Bundle()
      if (view.saveState(state) != null) tab.savedState = state
    }
    destroyWebView(tab)
    tab._hibernated.value = true
  }

  private fun destroyWebView(tab: BrowserTab) {
    val view = tab.webView ?: return
    tab.webView = null
    (view.parent as? android.view.ViewGroup)?.removeView(view)
    runCatching {
      view.stopLoading()
      view.destroy()
    }
  }

  // ---- Video takeover ----

  override fun onPlay(
    tab: BrowserTab,
    src: String?,
    title: String,
    drm: Boolean,
    userGesture: Boolean,
    reply: JavaScriptReplyProxy,
  ) {
    tab.sniffer.logEvent("play: src=$src gesture=$userGesture")
    if (tab.id != _activeId.value) {
      reply.post("pause")
      return
    }
    val host = SiteMatcher.host(tab.pageUrl)
    val isDrm = drm || tab.sniffer.drm.value
    if (isDrm || MediaClassifier.isBlockedForPlayback(tab.pageUrl)) {
      if (isDrm) _events.tryEmit(Event.ProtectedVideo(tab.id))
      return
    }
    val media = tab.sniffer.match(src)
    when (PlayTakeover.decide(prefs.mode(), prefs.siteChoice(host), tab.pageUrl, media != null, false, userGesture)) {
      PlayTakeover.Action.LET_PAGE_PLAY -> {
        if (prefs.stopAutoplay.get() && !userGesture) {
          reply.post("pause")
        }
      }
      PlayTakeover.Action.PLAY_IN_MPVGO -> {
        reply.post("pause")
        play(tab, media!!)
      }
      PlayTakeover.Action.ASK -> {
        if (media != null) {
          reply.post("pause")
          pendingReplies[tab.id] = reply
          _events.tryEmit(Event.Ask(tab.id, host, media))
        } else {
          // Streams often show up a moment after play(); keep the page quiet meanwhile.
          reply.post("mute")
          scope.launch {
            val found =
              withTimeoutOrNull(2_000) {
                tab.sniffer.media.first { it.isNotEmpty() }
                tab.sniffer.match(src)
              }
            reply.post("pause")
            pendingReplies[tab.id] = reply
            val mode = prefs.mode()
            if (found != null &&
              PlayTakeover.decide(mode, prefs.siteChoice(host), tab.pageUrl, true, false, userGesture) == PlayTakeover.Action.PLAY_IN_MPVGO
            ) {
              pendingReplies.remove(tab.id)
              play(tab, found)
            } else {
              _events.tryEmit(Event.Ask(tab.id, host, found))
            }
          }
        }
      }
    }
  }

  /** The user's answer to [Event.Ask]. [media] null with mpvGo means "nothing found". */
  fun answerAsk(
    tabId: Long,
    host: String,
    choice: SiteChoice,
    remember: Boolean,
    media: DetectedMedia?,
  ) {
    val tab = tab(tabId) ?: return
    val reply = pendingReplies.remove(tabId)
    if (remember && prefs.mode() == VideoTapMode.ASK_ONCE_PER_SITE) prefs.rememberSite(host, choice)
    val stream = media ?: tab.sniffer.match(null)
    if (choice == SiteChoice.MPVGO && stream != null) {
      play(tab, stream)
    } else {
      reply?.post("allowPlay")
    }
  }

  fun cancelAsk(tabId: Long) {
    pendingReplies.remove(tabId)
  }

  /** Opens [media] in the mpvGo player with the page's headers and cookies. */
  fun play(
    tab: BrowserTab,
    media: DetectedMedia,
  ) {
    pauseAll()
    if (!tab.isPrivate) library.markPlayed(tab.pageUrl)
    _events.tryEmit(Event.Launch(buildRequest(tab, media)))
  }

  fun buildRequest(
    tab: BrowserTab,
    media: DetectedMedia,
  ): MediaRequest {
    val frame = media.frameUrl.ifBlank { tab.pageUrl }
    val startInPip = prefs.playerMode() == BrowserVideoPlayerMode.SMALL_WINDOW
    return MediaRequest(
      url = media.url,
      title = media.pageTitle.ifBlank { tab._title.value }.takeIf { it.isNotBlank() },
      userAgent = tab.userAgent.takeIf { it.isNotBlank() },
      referer = frame,
      origin = GoWebViewFactory.originOf(frame),
      cookies = BrowserProfiles.cookies(tab.isPrivate, media.url, frame),
      pageUrl = tab.pageUrl,
      isPrivate = tab.isPrivate,
      skipRecents = !tab.isPrivate && !prefs.addWebVideosToRecents.get(),
      startInPip = startInPip,
    )
  }

  /** What to download for [media], with the page's headers and cookies. */
  fun draftFor(
    tab: BrowserTab,
    media: DetectedMedia,
  ): DownloadDraft = BrowserDownloads.draft(buildRequest(tab, media), media.mime)

  /** A plain link on the page, treated as a single-file stream. */
  fun linkMedia(
    tab: BrowserTab,
    url: String,
    mime: String? = null,
    title: String? = null,
  ): DetectedMedia =
    DetectedMedia(
      url = url,
      kind = MediaClassifier.classify(url, mime) ?: MediaKind.FILE,
      mime = mime,
      frameUrl = tab.pageUrl,
      pageUrl = tab.pageUrl,
      pageTitle = title.orEmpty(),
      size = -1,
      audioOnly = MediaClassifier.isAudioOnly(url, mime),
      seq = 0,
    )

  override fun onDownloadStart(
    tab: BrowserTab,
    url: String,
    contentDisposition: String?,
    mimeType: String?,
  ) {
    val mime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" }
    val name = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
    val isMedia = app.arju.mpvgo.downloader.engine.DownloadNaming.extensionOf(name) != null ||
      mime?.startsWith("video/") == true || mime?.startsWith("audio/") == true ||
      MediaClassifier.classify(url) != null
    val draft = draftFor(tab, linkMedia(tab, url, mime, name.substringBeforeLast('.')))
      .let { if (isMedia) it else it.copy(fileName = app.arju.mpvgo.downloader.engine.DownloadNaming.sanitize(name)) }
    _events.tryEmit(Event.Download(tab.id, draft))
  }

  override fun onLinkLongPress(
    tab: BrowserTab,
    url: String,
  ) {
    val media = MediaClassifier.classify(url)?.let { linkMedia(tab, url) }
    _events.tryEmit(Event.LinkMenu(tab.id, url, media))
  }

  // ---- TabHost ----

  override fun onCreateWindow(
    tab: BrowserTab,
    isUserGesture: Boolean,
    resultMsg: Message,
  ): Boolean {
    val child = newTab(private = tab.isPrivate, openerId = tab.id)
    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
    transport.webView = webViewFor(child)
    resultMsg.sendToTarget()
    return true
  }

  override fun onCloseWindow(tab: BrowserTab) {
    close(tab.id)
  }

  override fun onRendererGone(tab: BrowserTab) {
    Log.w(TAG, "Renderer gone for tab ${tab.id}")
    // Keep the URL; the next webViewFor() reloads it in a fresh WebView.
    tab.savedState = null
    destroyWebView(tab)
    tab._hibernated.value = true
  }

  override fun onExternalUrl(
    tab: BrowserTab,
    url: String,
    hasGesture: Boolean,
  ): Boolean {
    if (!hasGesture) return true
    val intent =
      runCatching {
        if (url.startsWith("intent:")) {
          Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            component = null
            selector = null
          }
        } else {
          Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
        }
      }.getOrNull() ?: return true
    try {
      context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
      intent.getStringExtra("browser_fallback_url")?.takeIf { it.startsWith("http") }?.let { load(tab, it) }
    } catch (e: SecurityException) {
      Log.w(TAG, "Can't open $url", e)
    }
    return true
  }

  override fun onShowCustomView(
    view: View,
    callback: WebChromeClient.CustomViewCallback,
  ) {
    _events.tryEmit(Event.Fullscreen(view, callback))
  }

  override fun onHideCustomView() {
    _events.tryEmit(Event.Fullscreen(null, null))
  }

  override fun onVisited(
    tab: BrowserTab,
    url: String,
  ) {
    if (!tab.isPrivate) library.recordVisit(url, "")
  }

  override fun onTitle(
    tab: BrowserTab,
    title: String,
  ) {
    if (!tab.isPrivate) library.updateTitle(tab.pageUrl, title)
  }

  override fun onPageFinished(
    tab: BrowserTab,
    view: WebView,
  ) {
    val y = tab.pendingScrollY
    if (y > 0) {
      tab.pendingScrollY = 0
      view.postDelayed({ view.scrollTo(0, y) }, SCROLL_RESTORE_DELAY)
    }
    if (!tab.isPrivate) {
      view.title?.let { library.updateTitle(tab.pageUrl, it) }
      saveSession()
    }
  }

  private suspend fun fetchText(
    url: String,
    referer: String,
    private: Boolean,
  ): String? =
    withContext(Dispatchers.IO) {
      val builder = Request.Builder().url(url).header("Referer", referer)
      active?.userAgent?.takeIf { it.isNotBlank() }?.let { builder.header("User-Agent", it) }
      BrowserProfiles.cookies(private, url)?.let { builder.header("Cookie", it) }
      http.newCall(builder.build()).execute().use { response ->
        val body = response.body ?: return@use null
        if (!response.isSuccessful || (body.contentLength() > MAX_PLAYLIST)) return@use null
        body.string().take(MAX_PLAYLIST.toInt())
      }
    }

  private fun JavaScriptReplyProxy.post(cmd: String) {
    runCatching { postMessage(JSONObject().put("cmd", cmd).toString()) }
  }

  companion object {
    private const val TAG = "TabManager"
    private const val MAX_PLAYLIST = 2_000_000L
    private const val SAVE_DELAY = 500L
    private const val SCROLL_RESTORE_DELAY = 300L
  }
}

/** Turns what the user typed in the address bar into a URL. */
object UrlInput {
  private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
  private val LOCAL = Regex("^(localhost|\\d{1,3}(\\.\\d{1,3}){3})(:\\d+)?(/.*)?$")
  private val HOST_LIKE = Regex("^[^\\s/]+\\.[^\\s/]{2,}(/.*)?$")

  fun toUrl(
    input: String,
    searchTemplate: String = "https://duckduckgo.com/?q=%s",
  ): String {
    val text = input.trim()
    if (SCHEME.containsMatchIn(text)) return text
    if (text.startsWith("about:")) return text
    // Local servers rarely have TLS.
    if (LOCAL.matches(text)) return "http://$text"
    if (!text.contains(' ') && HOST_LIKE.matches(text)) return "https://$text"
    return searchTemplate.replace("%s", java.net.URLEncoder.encode(text, "UTF-8"))
  }
}
