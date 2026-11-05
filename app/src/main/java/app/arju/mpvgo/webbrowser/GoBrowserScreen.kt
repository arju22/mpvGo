package app.arju.mpvgo.webbrowser

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.arju.mpvgo.core.preferences.preference.collectAsState
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.downloader.DownloadCounts
import app.arju.mpvgo.downloader.DownloadManager
import app.arju.mpvgo.downloader.DownloadNotifications
import app.arju.mpvgo.downloader.data.DownloadEntity
import app.arju.mpvgo.downloader.ui.DownloadDraft
import app.arju.mpvgo.downloader.ui.DownloadPlayback
import app.arju.mpvgo.downloader.ui.DownloadRequests
import app.arju.mpvgo.downloader.ui.toNewDownload
import app.arju.mpvgo.webbrowser.core.BrowserBackAction
import app.arju.mpvgo.webbrowser.core.BrowserDownloads
import app.arju.mpvgo.webbrowser.core.LinkRefresh
import app.arju.mpvgo.webbrowser.core.VideoTapMode
import app.arju.mpvgo.webbrowser.ui.AlreadyDownloadedDialog
import app.arju.mpvgo.webbrowser.ui.CantDownloadDialog
import app.arju.mpvgo.webbrowser.ui.DetectionLogDialog
import app.arju.mpvgo.webbrowser.ui.FoundVideosSheet
import app.arju.mpvgo.webbrowser.ui.LinkAction
import app.arju.mpvgo.webbrowser.ui.LinkMenuSheet
import app.arju.mpvgo.webbrowser.ui.VideoAskDialog
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import android.content.ClipboardManager
import app.arju.mpvgo.webbrowser.core.BrowserTab
import app.arju.mpvgo.webbrowser.core.ClipboardLink
import app.arju.mpvgo.webbrowser.core.Suggestions
import app.arju.mpvgo.webbrowser.core.UrlSuggestion
import app.arju.mpvgo.webbrowser.settings.BrowserSettingsScreen
import app.arju.mpvgo.webbrowser.core.DetectedMedia
import app.arju.mpvgo.webbrowser.core.SearchEngine
import app.arju.mpvgo.webbrowser.core.SiteChoice
import app.arju.mpvgo.webbrowser.core.SpeedDial
import app.arju.mpvgo.webbrowser.core.SpeedDials
import app.arju.mpvgo.webbrowser.core.TabManager
import app.arju.mpvgo.webbrowser.core.TopBarButton
import app.arju.mpvgo.webbrowser.library.BookmarkEntity
import app.arju.mpvgo.webbrowser.library.BookmarkTree
import app.arju.mpvgo.webbrowser.library.BrowserLibrary
import app.arju.mpvgo.webbrowser.ui.BookmarkEditDialog
import app.arju.mpvgo.webbrowser.ui.BrowserAction
import app.arju.mpvgo.webbrowser.ui.BrowserLibraryPage
import app.arju.mpvgo.webbrowser.ui.BrowserTopBar
import app.arju.mpvgo.webbrowser.ui.FindBar
import app.arju.mpvgo.webbrowser.ui.LibraryTab
import app.arju.mpvgo.webbrowser.ui.OpenMode
import app.arju.mpvgo.webbrowser.ui.PrivateColors
import app.arju.mpvgo.webbrowser.ui.PullRefreshLayout
import app.arju.mpvgo.webbrowser.ui.SiteSettingsContent
import app.arju.mpvgo.webbrowser.ui.StartPage
import app.arju.mpvgo.webbrowser.ui.TabsDrawer
import app.arju.mpvgo.webbrowser.ui.TopBarState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Go Browser: tabs drawer, address bar, start page and the WebView of the active tab. */
@Serializable
data class GoBrowserScreen(val url: String? = null) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tabs = koinInject<TabManager>()
    val adPrefs = koinInject<AdBlockPreferences>()
    val library = koinInject<BrowserLibrary>()
    val prefs = tabs.prefs
    val tabList by tabs.tabs.collectAsState()
    val activeId by tabs.activeId.collectAsState()
    val tab = tabList.firstOrNull { it.id == activeId }

    var ask by remember { mutableStateOf<TabManager.Event.Ask?>(null) }
    var showMedia by remember { mutableStateOf(false) }
    var showSite by remember { mutableStateOf(false) }
    var finding by remember { mutableStateOf(false) }
    var findMatches by remember { mutableStateOf(0 to 0) }
    var editRequest by remember { mutableIntStateOf(0) }
    var pull by remember { mutableFloatStateOf(0f) }
    var fullscreen by remember { mutableStateOf<Pair<android.view.View, WebChromeClient.CustomViewCallback>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var drawerPrivate by remember { mutableStateOf(false) }
    var libraryPage by rememberSaveable { mutableStateOf<LibraryTab?>(null) }
    var bookmarkEdit by remember { mutableStateOf<BookmarkEntity?>(null) }
    val downloadManager = koinInject<DownloadManager>()
    val downloads by remember { downloadManager.downloads }.collectAsState(initial = emptyList())
    var already by remember { mutableStateOf<Pair<DownloadEntity, DownloadDraft>?>(null) }
    var cantDownload by remember { mutableStateOf<String?>(null) }
    var linkMenu by remember { mutableStateOf<TabManager.Event.LinkMenu?>(null) }
    var showDetectionLog by remember { mutableStateOf(false) }

    /** Every browser download goes through here: blocked sites, then "Already downloaded", then the sheet. */
    fun download(
      page: String,
      draft: DownloadDraft,
      drm: Boolean = false,
    ) {
      BrowserDownloads.blocked(page, draft.url, drm)?.let {
        cantDownload = it.message
        return
      }
      BrowserDownloads.findCompleted(downloads, draft.url)?.let {
        already = it to draft
        return
      }
      DownloadRequests.request(draft)
    }

    fun copyLink(link: String) {
      val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
      clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Link", link))
      Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
    }

    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
      if (opened) return@LaunchedEffect
      opened = true
      if (url != null) {
        tabs.restoreSession()
        tabs.newTab(url)
      } else if (tabs.active == null && !tabs.restoreSession()) {
        tabs.newTab()
      }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(context, lifecycle) {
      tabs.attach(context)
      val observer =
        LifecycleEventObserver { _, event ->
          when (event) {
            Lifecycle.Event.ON_PAUSE -> tabs.pauseAll()
            Lifecycle.Event.ON_RESUME -> tabs.resumeActive()
            else -> Unit
          }
        }
      lifecycle.addObserver(observer)
      onDispose {
        lifecycle.removeObserver(observer)
        tabs.pauseAll()
        tabs.detach(context)
      }
    }
    LaunchedEffect(Unit) {
      tabs.events.collect { event ->
        when (event) {
          is TabManager.Event.Ask -> ask = event
          is TabManager.Event.Launch -> context.startActivity(event.request.toIntent(context))
          is TabManager.Event.ProtectedVideo -> message = "This video is protected (DRM) and plays only on the page."
          is TabManager.Event.PopupBlocked -> Unit
          is TabManager.Event.Download -> {
            val from = tabs.tab(event.tabId)
            download(from?.pageUrl.orEmpty(), event.draft)
          }
          is TabManager.Event.LinkMenu -> linkMenu = event
          is TabManager.Event.Fullscreen -> {
            fullscreen?.second?.onCustomViewHidden()
            fullscreen = if (event.view != null && event.callback != null) event.view to event.callback else null
            (context as? Activity)?.requestedOrientation =
              if (fullscreen != null) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
          }
        }
      }
    }
    // Closing every tab leaves a fresh start page rather than an empty browser.
    LaunchedEffect(tabList.isEmpty(), opened) {
      if (opened && tabs.tabs.value.isEmpty()) tabs.newTab()
    }
    // "Refresh link": the failed download's source page is open; wait for its video and swap the link.
    val refresh by LinkRefresh.pending.collectAsState()
    LaunchedEffect(refresh, tab) {
      val request = refresh ?: return@LaunchedEffect
      val current = tab ?: return@LaunchedEffect
      val entity = downloadManager.get(request.downloadId)
      if (entity == null) {
        LinkRefresh.finish(request)
        return@LaunchedEffect
      }
      val site = SiteMatcher.host(request.pageUrl).removePrefix("www.")
      val pick: (List<DetectedMedia>) -> DetectedMedia? = { found ->
        if (SiteMatcher.host(current.pageUrl).removePrefix("www.") != site) {
          null
        } else {
          BrowserDownloads.pickRefresh(found.filter { it.url != entity.url }, entity.url, entity.isHls)
        }
      }
      val quick = withTimeoutOrNull(4_000) { current.sniffer.media.map(pick).filterNotNull().first() }
      if (quick == null) Toast.makeText(context, "Play the video on this page to refresh the download link", Toast.LENGTH_LONG).show()
      val found = quick ?: current.sniffer.media.map(pick).filterNotNull().first()
      val fresh = tabs.buildRequest(current, found)
      downloadManager.updateLink(entity.id, fresh.url, fresh.userAgent, fresh.referer, fresh.cookies)
      DownloadNotifications.cancel(context, entity.id)
      LinkRefresh.finish(request)
      Toast.makeText(context, "Link refreshed. \"${entity.fileName}\" is downloading again.", Toast.LENGTH_LONG).show()
    }

    val webView = tab?.let { tabs.webViewFor(it) }
    val noFlow = remember { MutableStateFlow(false) }
    val canGoBack by (tab?.canGoBack ?: noFlow).collectAsState()
    val canGoForward by (tab?.canGoForward ?: noFlow).collectAsState()
    val tabUrl by (tab?.url ?: remember { MutableStateFlow("") }).collectAsState()
    val startPage = tabUrl.isEmpty()

    fun closeFind() {
      finding = false
      findMatches = 0 to 0
      webView?.clearMatches()
    }

    fun newTab(private: Boolean) {
      closeFind()
      drawerPrivate = private
      tabs.newTab(private = private)
    }

    fun exitBrowser() {
      tabs.onExitBrowser()
      backstack.removeLastOrNull()
    }

    LaunchedEffect(Unit) { tabs.applySettings() }
    // Home pauses the WebView; resume it once the tab shows a page again (load, Back, Forward).
    LaunchedEffect(webView, startPage) { if (!startPage) webView?.onResume() }

    LaunchedEffect(drawer.targetValue) {
      if (drawer.targetValue == DrawerValue.Open) {
        drawerPrivate = (tabs.active ?: tab)?.isPrivate == true
      }
    }

    val hasValidOpener = tab?.openerId != null && tabList.any { it.id == tab.openerId }
    BackHandler {
      when (BrowserBackAction.decide(
        drawerOpen = drawer.isOpen,
        libraryOpen = libraryPage != null,
        mediaOpen = showMedia,
        siteOpen = showSite,
        findOpen = finding,
        fullscreenOpen = fullscreen != null,
        canGoBack = canGoBack,
        hasValidOpener = hasValidOpener,
      )) {
        BrowserBackAction.Action.CLOSE_DRAWER -> scope.launch { drawer.close() }
        BrowserBackAction.Action.CLOSE_LIBRARY -> libraryPage = null
        BrowserBackAction.Action.CLOSE_MEDIA -> showMedia = false
        BrowserBackAction.Action.CLOSE_SITE -> showSite = false
        BrowserBackAction.Action.CLOSE_FIND -> closeFind()
        BrowserBackAction.Action.CLOSE_FULLSCREEN -> fullscreen?.second?.onCustomViewHidden()
        BrowserBackAction.Action.WEBVIEW_GO_BACK -> webView?.goBack()
        BrowserBackAction.Action.CLOSE_TAB_TO_OPENER -> tab?.let { tabs.close(it.id) }
        BrowserBackAction.Action.EXIT_BROWSER -> exitBrowser()
      }
    }

    val activeDownloads by DownloadCounts.active.collectAsState()
    val topButtons by prefs.topBarButtons.collectAsState()
    val allowedSites by adPrefs.allowedSites.collectAsState()
    val desktop by (tab?.desktop ?: noFlow).collectAsState()
    val loading by (tab?.loading ?: noFlow).collectAsState()
    val progress by (tab?.progress ?: remember { MutableStateFlow(0) }).collectAsState()
    val isPrivate = tab?.isPrivate == true
    val adBlockOnSite = !SiteMatcher.isAllowed(tabUrl, allowedSites)
    val bookmark by remember(tabUrl) { library.bookmarkFor(tabUrl) }.collectAsState(initial = null)

    fun addToStartPage(
      title: String,
      address: String,
    ): Boolean {
      val dials = prefs.dials()
      if (dials.size >= SpeedDials.MAX || dials.any { it.url == address }) return false
      prefs.saveDials(dials + SpeedDial(title.take(40), address))
      return true
    }

    fun onAction(action: BrowserAction) {
      val t = tabs.active ?: tab ?: return
      when (action) {
        BrowserAction.TABS -> {
          drawerPrivate = t.isPrivate
          scope.launch { drawer.open() }
        }
        BrowserAction.RELOAD -> webView?.reload()
        BrowserAction.STOP -> webView?.stopLoading()
        BrowserAction.FORWARD -> webView?.goForward()
        BrowserAction.SHARE -> {
          val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, tabUrl)
          context.startActivity(Intent.createChooser(send, null))
        }
        BrowserAction.NEW_TAB -> newTab(false)
        BrowserAction.NEW_PRIVATE_TAB -> newTab(true)
        BrowserAction.DOWNLOADS -> DownloadCounts.requestOpen()
        BrowserAction.FIND -> if (!startPage) finding = true
        BrowserAction.AD_BLOCK -> {
          val host = SiteMatcher.host(tabUrl)
          if (host.isNotEmpty()) {
            adPrefs.allowedSites.set(if (adBlockOnSite) allowedSites + host else allowedSites.filterNot { SiteMatcher.host(it) == host }.toSet())
            webView?.reload()
          }
        }
        BrowserAction.DESKTOP -> tabs.setDesktop(t, !desktop)
        BrowserAction.SITE_SETTINGS -> showSite = true
        BrowserAction.CLOSE_BROWSER -> exitBrowser()
        BrowserAction.HOME -> {
          closeFind()
          tabs.goHome(t)
        }
        BrowserAction.BOOKMARK_PAGE ->
          if (!startPage) bookmarkEdit = bookmark ?: BookmarkEntity(title = t.title.value.ifBlank { tabUrl }, url = tabUrl)
        BrowserAction.BOOKMARKS -> libraryPage = LibraryTab.BOOKMARKS
        BrowserAction.HISTORY -> libraryPage = LibraryTab.HISTORY
        BrowserAction.SETTINGS -> backstack.add(BrowserSettingsScreen)
      }
    }

    val showTabCount by prefs.showTabCount.collectAsState()
    val swipeAddressBar by prefs.swipeAddressBar.collectAsState()
    val swipeTabsPanel by prefs.swipeTabsPanel.collectAsState()
    val searchSuggestions by prefs.searchSuggestions.collectAsState()
    val suggestCopied by prefs.suggestCopiedLink.collectAsState()
    val blockedOnPage by (tab?.blocked ?: remember { MutableStateFlow(0) }).collectAsState()
    var copiedLink by remember { mutableStateOf<String?>(null) }

    // Suggestions come only from this device (history, bookmarks, clipboard); nothing is sent to a search engine.
    suspend fun suggest(query: String): List<UrlSuggestion> {
      if (query.isEmpty()) {
        copiedLink =
          if (suggestCopied) {
            val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
            clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let { ClipboardLink.extract(it.toString()) }
          } else {
            null
          }
      }
      val copied = copiedLink?.takeIf { it != tabUrl && (query.isEmpty() || it.contains(query, ignoreCase = true)) }
      if (!searchSuggestions || isPrivate) return Suggestions.merge(copied, emptyList(), emptyList())
      val (marks, visits) = library.search(query)
      return Suggestions.merge(copied, marks, visits)
    }

    val rootColor = if (isPrivate) PrivateColors.background else MaterialTheme.colorScheme.surface
    ModalNavigationDrawer(
      drawerState = drawer,
      gesturesEnabled = drawer.isOpen || drawer.targetValue == DrawerValue.Open || (libraryPage == null && swipeTabsPanel),
      drawerContent = {
        TabsDrawer(
          normal = tabList.filterNot { it.isPrivate },
          private = tabList.filter { it.isPrivate },
          activeId = activeId,
          showPrivate = drawerPrivate,
          canGoBack = canGoBack,
          canGoForward = canGoForward,
          onShowPrivate = { drawerPrivate = it },
          onNewPrivateTab = {
            newTab(private = true)
            editRequest++
            scope.launch { drawer.close() }
          },
          onSelect = {
            closeFind()
            tabs.select(it.id)
            scope.launch { drawer.close() }
          },
          onClose = { tabToClose ->
            tabs.close(tabToClose.id)
            if (drawerPrivate && tabList.none { it.id != tabToClose.id && it.isPrivate }) {
              drawerPrivate = false
            }
          },
          onCloseAll = {
            tabs.closeAll(drawerPrivate)
            if (drawerPrivate) {
              drawerPrivate = false
            }
          },
          onExit = ::exitBrowser,
          onBack = { webView?.goBack() },
          onHome = {
            tab?.let(tabs::goHome)
            scope.launch { drawer.close() }
          },
          onForward = { webView?.goForward() },
          onNewTab = {
            newTab(drawerPrivate)
            scope.launch { drawer.close() }
          },
        )
      },
    ) {
      Box(Modifier.fillMaxSize().background(rootColor)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
          if (tab != null) {
            if (finding) {
              FindBar(
                isPrivate = isPrivate,
                matches = findMatches,
                onQuery = { q -> webView?.findAllAsync(q) },
                onNext = { forward -> webView?.findNext(forward) },
                onClose = ::closeFind,
              )
            } else {
              val siblings = tabs.siblings(tab)
              val tabIndex = siblings.indexOfFirst { it.id == activeId }
              val tabPositionText = if (tabIndex >= 0 && siblings.size > 1) "${tabIndex + 1}/${siblings.size}" else null
              BrowserTopBar(
                state =
                  TopBarState(
                    url = tabUrl,
                    isPrivate = isPrivate,
                    loading = loading,
                    progress = progress,
                    tabCount = siblings.size,
                    tabPosition = tabPositionText,
                    activeTabId = activeId,
                    canGoForward = canGoForward,
                    buttons = TopBarButton.parse(topButtons),
                    activeDownloads = activeDownloads,
                    adBlockOnSite = adBlockOnSite,
                    desktop = desktop,
                    bookmarked = bookmark != null,
                    blocked = blockedOnPage,
                    showTabCount = showTabCount,
                    swipeToSwitch = swipeAddressBar,
                  ),
                suggest = ::suggest,
                onAction = ::onAction,
                onSubmit = { text -> tabs.load(tab, text) },
                onSwipeTab = { dir ->
                  closeFind()
                  tabs.step(dir)
                },
                editRequest = editRequest,
              )
            }
          }
          Box(Modifier.fillMaxWidth().weight(1f)) {
            if (tab != null && webView != null) {
              if (startPage) {
                BrowserStartPage(tab, tabs, onSearched = { })
              }
              val pullToRefresh by prefs.pullToRefresh.collectAsState()
              AndroidView(
                factory = { PullRefreshLayout(it) },
                update = { frame ->
                  frame.pullEnabled = pullToRefresh && !startPage
                  frame.onPull = { pull = it }
                  frame.onRefresh = { webView.reload() }
                  if (webView.parent !== frame) {
                    (webView.parent as? ViewGroup)?.removeView(webView)
                    frame.removeAllViews()
                    frame.addView(webView, FrameLayout.LayoutParams(-1, -1))
                    webView.setFindListener { active, count, _ -> findMatches = active to count }
                  }
                },
                modifier = if (startPage) Modifier.height(0.dp) else Modifier.fillMaxSize(),
              )
              if (pull > 0f) {
                Surface(
                  shape = CircleShape,
                  shadowElevation = 4.dp,
                  modifier = Modifier.align(Alignment.TopCenter).offset(y = 64.dp * pull.coerceAtMost(1f)).size(40.dp),
                ) {
                  CircularProgressIndicator(progress = { pull.coerceAtMost(1f) }, modifier = Modifier.padding(8.dp))
                }
              }
              if (!startPage && swipeTabsPanel) {
                LeftEdgeSwipe(Modifier.align(Alignment.CenterStart)) {
                  drawerPrivate = (tabs.active ?: tab).isPrivate
                  scope.launch { drawer.open() }
                }
              }
              val showFab by prefs.floatingMediaButton.collectAsState()
              if (showFab) {
                MediaFab(
                  tab = tab,
                  modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                  onClick = { showMedia = true },
                  onLongClick = { showDetectionLog = true },
                )
              }
            }
          }
        }
        fullscreen?.let { (view, _) ->
          AndroidView(
            factory = { FrameLayout(it).apply { setBackgroundColor(android.graphics.Color.BLACK) } },
            update = { frame ->
              if (view.parent !== frame) {
                (view.parent as? ViewGroup)?.removeView(view)
                frame.removeAllViews()
                frame.addView(view, FrameLayout.LayoutParams(-1, -1))
              }
            },
            modifier = Modifier.fillMaxSize(),
          )
        }
        libraryPage?.let { page ->
          BrowserLibraryPage(
            initial = page,
            library = library,
            onOpen = { address, mode ->
              libraryPage = null
              closeFind()
              when (mode) {
                OpenMode.CURRENT -> tab?.let { tabs.load(it, address) } ?: tabs.newTab(address)
                OpenMode.NEW_TAB -> tabs.newTab(address)
                OpenMode.PRIVATE_TAB -> tabs.newTab(address, private = true)
              }
            },
            onOpenMany = { urls ->
              libraryPage = null
              closeFind()
              urls.forEachIndexed { i, u -> tabs.newTab(u, select = i == urls.lastIndex) }
              Toast.makeText(context, "Opened ${urls.size} tabs", Toast.LENGTH_SHORT).show()
            },
            onAddToStartPage = ::addToStartPage,
            onClearData = { request ->
              tabs.clearBrowsingData(request.cookies, request.cache)
              if (request.siteSettings) {
                prefs.siteChoices.set(emptySet())
                adPrefs.allowedSites.set(emptySet())
              }
            },
            onClose = { libraryPage = null },
          )
        }
      }
    }

    bookmarkEdit?.let { item ->
      val all by remember { library.bookmarks }.collectAsState(initial = emptyList())
      BookmarkEditDialog(
        initial = item,
        isNew = item.id == 0L,
        folders = BookmarkTree.folders(all),
        onSave = { saved, toStart ->
          scope.launch { library.save(saved) }
          if (toStart) addToStartPage(saved.title, saved.url)
          bookmarkEdit = null
          if (saved.id == 0L) Toast.makeText(context, "Bookmark added", Toast.LENGTH_SHORT).show()
        },
        onDelete = {
          scope.launch { library.deleteBookmarks(listOf(item.id)) }
          bookmarkEdit = null
        },
        onDismiss = { bookmarkEdit = null },
      )
    }

    if (showMedia && tab != null) {
      val media by tab.sniffer.media.collectAsState()
      val hasHidden by tab.sniffer.hasHiddenPlaylist.collectAsState()
      val blobMse by tab.sniffer.blobMseDetected.collectAsState()
      FoundVideosSheet(
        host = SiteMatcher.host(tabUrl),
        media = media,
        hasHiddenPlaylist = hasHidden,
        blobMseDetected = blobMse,
        isDownloaded = { BrowserDownloads.findCompleted(downloads, it.url) != null },
        onPlay = {
          showMedia = false
          tabs.play(tab, it, fromFoundVideos = true)
        },
        onDownload = {
          showMedia = false
          download(tab.pageUrl, tabs.draftFor(tab, it), tab.sniffer.drm.value)
        },
        onDownloadAll = { items ->
          showMedia = false
          val blocked = items.firstNotNullOfOrNull { BrowserDownloads.blocked(tab.pageUrl, it.url, tab.sniffer.drm.value) }
          if (blocked != null) {
            cantDownload = blocked.message
          } else {
            val drafts = items.map { tabs.draftFor(tab, it) }
            scope.launch { drafts.forEach { downloadManager.enqueue(it.toNewDownload()) } }
            Toast.makeText(context, if (drafts.size == 1) "Downloading 1 video" else "Downloading ${drafts.size} videos", Toast.LENGTH_SHORT).show()
          }
        },
        onClear = {
          tab.sniffer.clear()
          showMedia = false
        },
        onDismiss = { showMedia = false },
        onShowDetectionLog = {
          showMedia = false
          showDetectionLog = true
        },
      )
    }

    if (showDetectionLog && tab != null) {
      val events by tab.sniffer.detectionLog.collectAsState()
      DetectionLogDialog(
        events = events,
        onDismiss = { showDetectionLog = false },
      )
    }

    if (showSite && tab != null) {
      val host = SiteMatcher.host(tabUrl)
      val blocked by tab.blocked.collectAsState()
      val popups by adPrefs.blockPopups.collectAsState()
      val choices by prefs.siteChoices.collectAsState()
      ModalBottomSheet(onDismissRequest = { showSite = false }) {
        SiteSettingsContent(
          host = host,
          blocked = blocked,
          adBlockOn = adBlockOnSite,
          blockPopups = popups,
          videoChoice = choices.let { prefs.siteChoice(host) },
          onAdBlock = { on -> if (on != adBlockOnSite) onAction(BrowserAction.AD_BLOCK) },
          onBlockPopups = { adPrefs.blockPopups.set(it) },
          onVideoChoice = { prefs.rememberSite(host, it) },
        )
      }
    }

    ask?.let { event ->
      val askTab = tabs.tab(event.tabId)
      VideoAskDialog(
        host = event.host,
        title = event.media?.pageTitle?.ifBlank { null } ?: askTab?.title?.value.orEmpty(),
        media = event.media,
        showRemember = prefs.mode() == VideoTapMode.ASK_ONCE_PER_SITE,
        onPlay = { always ->
          tabs.answerAsk(event.tabId, event.host, SiteChoice.MPVGO, always, event.media)
          ask = null
        },
        onDownload = {
          tabs.cancelAsk(event.tabId)
          ask = null
          val media = event.media
          if (askTab != null && media != null) download(askTab.pageUrl, tabs.draftFor(askTab, media), askTab.sniffer.drm.value)
        },
        onWebPlayer = { always ->
          tabs.answerAsk(event.tabId, event.host, SiteChoice.WEB, always, event.media)
          ask = null
        },
        onCopyLink = { event.media?.url?.let(::copyLink) },
        onDismiss = {
          tabs.cancelAsk(event.tabId)
          ask = null
        },
      )
    }

    linkMenu?.let { event ->
      val menuTab = tabs.tab(event.tabId)
      LinkMenuSheet(
        url = event.url,
        isPrivateTab = menuTab?.isPrivate == true,
        isVideo = event.media != null,
        onAction = { action ->
          linkMenu = null
          when (action) {
            LinkAction.NEW_TAB -> tabs.newTab(event.url, private = menuTab?.isPrivate == true, select = false, openerId = event.tabId)
            LinkAction.PRIVATE_TAB -> tabs.newTab(event.url, private = true, select = false, openerId = event.tabId)
            LinkAction.PLAY -> if (menuTab != null && event.media != null) tabs.play(menuTab, event.media)
            LinkAction.DOWNLOAD ->
              if (menuTab != null) {
                download(menuTab.pageUrl, tabs.draftFor(menuTab, event.media ?: tabs.linkMedia(menuTab, event.url)))
              }
            LinkAction.COPY -> copyLink(event.url)
            LinkAction.SHARE ->
              context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, event.url), null),
              )
          }
          if (action == LinkAction.NEW_TAB || action == LinkAction.PRIVATE_TAB) {
            Toast.makeText(context, "Opened in background tab", Toast.LENGTH_SHORT).show()
          }
        },
        onDismiss = { linkMenu = null },
      )
    }

    already?.let { (entity, draft) ->
      AlreadyDownloadedDialog(
        fileName = entity.fileName,
        onPlay = {
          already = null
          DownloadPlayback.playCompleted(context, entity)
        },
        onDownloadAgain = {
          already = null
          DownloadRequests.request(draft.copy(forceSheet = true))
        },
        onDismiss = { already = null },
      )
    }

    cantDownload?.let {
      CantDownloadDialog(it) { cantDownload = null }
    }

    message?.let {
      AlertDialog(
        onDismissRequest = { message = null },
        text = { Text(it) },
        confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
      )
    }
  }
}

@Composable
private fun BrowserStartPage(
  tab: BrowserTab,
  tabs: TabManager,
  onSearched: () -> Unit,
) {
  val prefs = tabs.prefs
  val engineName by prefs.searchEngine.collectAsState()
  val custom by prefs.customSearchUrl.collectAsState()
  val rawDials by prefs.speedDials.collectAsState()
  val siteTiles by prefs.siteTiles.collectAsState()
  StartPage(
    isPrivate = tab.isPrivate,
    engine = runCatching { SearchEngine.valueOf(engineName) }.getOrDefault(SearchEngine.GOOGLE),
    customSearchUrl = custom,
    dials = SpeedDials.decode(rawDials),
    showDials = siteTiles,
    onSearch = {
      tabs.load(tab, it)
      onSearched()
    },
    onOpen = { url, newTab, private ->
      if (newTab) tabs.newTab(url, private = private) else tabs.load(tab, url)
    },
    onDialsChange = prefs::saveDials,
    onEngineChange = { engine, customUrl ->
      customUrl?.let(prefs.customSearchUrl::set)
      prefs.searchEngine.set(engine.name)
    },
  )
}

/** A thin strip on the left edge of the page: swiping right from it opens the tabs drawer. */
@Composable
private fun LeftEdgeSwipe(
  modifier: Modifier,
  onOpen: () -> Unit,
) {
  val threshold = with(LocalDensity.current) { 48.dp.toPx() }
  var drag by remember { mutableFloatStateOf(0f) }
  Box(
    modifier
      .width(16.dp)
      .fillMaxHeight()
      .pointerInput(Unit) {
        detectHorizontalDragGestures(
          onDragStart = { drag = 0f },
          onDragEnd = { if (drag > threshold) onOpen() },
        ) { change, amount ->
          change.consume()
          drag += amount
        }
      },
  )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaFab(
  tab: BrowserTab,
  modifier: Modifier,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
) {
  val media by tab.sniffer.media.collectAsState()
  val hasHidden by tab.sniffer.hasHiddenPlaylist.collectAsState()
  val blobMse by tab.sniffer.blobMseDetected.collectAsState()
  val count = media.count { !it.audioOnly }
  if (count == 0 && !hasHidden && !blobMse) return
  val live by koinInject<DownloadManager>().live.collectAsState()
  val progress = BrowserDownloads.progress(live.values)
  val ringColor = MaterialTheme.colorScheme.primary
  val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
  val angle =
    if (progress != null && progress < 0f) {
      androidx.compose.animation.core.rememberInfiniteTransition(label = "dlRing").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.LinearEasing)),
        label = "dlRingAngle",
      ).value
    } else {
      0f
    }
  // Drawn outside the FAB's bounds so the button keeps its place.
  val ring =
    Modifier.drawBehind {
      val p = progress ?: return@drawBehind
      val radius = 38.dp.toPx()
      val stroke = androidx.compose.ui.graphics.drawscope.Stroke(4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
      val topLeft = center - androidx.compose.ui.geometry.Offset(radius, radius)
      val arcSize = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
      drawCircle(trackColor, radius, style = stroke)
      if (p < 0f) {
        drawArc(ringColor, angle - 90f, 90f, false, topLeft, arcSize, style = stroke)
      } else {
        drawArc(ringColor, -90f, 360f * p, false, topLeft, arcSize, style = stroke)
      }
    }
  Box(modifier.then(ring), contentAlignment = Alignment.Center) {
    Surface(
      shape = FloatingActionButtonDefaults.shape,
      color = MaterialTheme.colorScheme.primaryContainer,
      shadowElevation = 6.dp,
      modifier = Modifier
        .size(56.dp)
        .combinedClickable(
          onClick = onClick,
          onLongClick = onLongClick,
          onClickLabel = "Found videos",
          onLongClickLabel = "Detection log",
        ),
    ) {
      Box(contentAlignment = Alignment.Center) {
        if (count > 0) {
          BadgedBox(badge = { Badge { Text("$count") } }) {
            Icon(Icons.Filled.SmartDisplay, if (count == 1) "1 video found" else "$count videos found")
          }
        } else {
          BadgedBox(badge = { Badge { Text("!") } }) {
            Icon(Icons.Filled.SmartDisplay, "Video detected")
          }
        }
      }
    }
  }
}
