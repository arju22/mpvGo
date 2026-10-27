package app.arju.mpvgo.webbrowser

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import app.arju.mpvgo.webbrowser.core.BrowserTab
import app.arju.mpvgo.webbrowser.core.DetectedMedia
import app.arju.mpvgo.webbrowser.core.SearchEngine
import app.arju.mpvgo.webbrowser.core.SiteChoice
import app.arju.mpvgo.webbrowser.core.SpeedDials
import app.arju.mpvgo.webbrowser.core.TabManager
import app.arju.mpvgo.webbrowser.core.TopBarButton
import app.arju.mpvgo.webbrowser.ui.BrowserAction
import app.arju.mpvgo.webbrowser.ui.BrowserTopBar
import app.arju.mpvgo.webbrowser.ui.FindBar
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

    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
      if (opened) return@LaunchedEffect
      opened = true
      if (url != null) tabs.newTab(url) else if (tabs.active == null) tabs.newTab()
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
      tabs.newTab(private = private)
    }

    BackHandler {
      when {
        drawer.isOpen -> scope.launch { drawer.close() }
        finding -> closeFind()
        fullscreen != null -> fullscreen?.second?.onCustomViewHidden()
        canGoBack -> webView?.goBack()
        else -> backstack.removeLastOrNull()
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

    fun onAction(action: BrowserAction) {
      val t = tab ?: return
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
        BrowserAction.CLOSE_BROWSER -> backstack.removeLastOrNull()
        BrowserAction.BOOKMARK_PAGE, BrowserAction.BOOKMARKS, BrowserAction.HISTORY, BrowserAction.SETTINGS ->
          Toast.makeText(context, "Coming in the next update", Toast.LENGTH_SHORT).show()
      }
    }

    val rootColor = if (isPrivate) PrivateColors.background else MaterialTheme.colorScheme.surface
    ModalNavigationDrawer(
      drawerState = drawer,
      gesturesEnabled = true,
      drawerContent = {
        TabsDrawer(
          normal = tabList.filterNot { it.isPrivate },
          private = tabList.filter { it.isPrivate },
          activeId = activeId,
          showPrivate = drawerPrivate,
          canGoBack = canGoBack,
          canGoForward = canGoForward,
          onShowPrivate = { drawerPrivate = it },
          onSelect = {
            closeFind()
            tabs.select(it.id)
            scope.launch { drawer.close() }
          },
          onClose = { tabs.close(it.id) },
          onCloseAll = { tabs.closeAll(drawerPrivate) },
          onExit = { backstack.removeLastOrNull() },
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
              BrowserTopBar(
                state =
                  TopBarState(
                    url = tabUrl,
                    isPrivate = isPrivate,
                    loading = loading,
                    progress = progress,
                    tabCount = tabs.siblings(tab).size,
                    canGoForward = canGoForward,
                    buttons = TopBarButton.parse(topButtons),
                    activeDownloads = activeDownloads,
                    adBlockOnSite = adBlockOnSite,
                    desktop = desktop,
                  ),
                onAction = ::onAction,
                onSubmit = { text -> tabs.load(tab, text) },
                onSwipeTab = { dir ->
                  closeFind()
                  tabs.step(tab, dir)
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
              if (!startPage) {
                LeftEdgeSwipe(Modifier.align(Alignment.CenterStart)) {
                  drawerPrivate = tab.isPrivate
                  scope.launch { drawer.open() }
                }
              }
              val showFab by prefs.floatingMediaButton.collectAsState()
              if (showFab) MediaFab(tab, Modifier.align(Alignment.BottomEnd).padding(16.dp)) { showMedia = true }
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
      }
    }

    if (showMedia && tab != null) {
      val media by tab.sniffer.media.collectAsState()
      ModalBottomSheet(onDismissRequest = { showMedia = false }) {
        Text("Videos on this page", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
        LazyColumn {
          items(media.sortedByDescending { it.seq }, key = { it.key }) { item ->
            ListItem(
              headlineContent = { Text(item.url.substringAfterLast('/').substringBefore('?').ifBlank { item.url }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
              supportingContent = { Text(listOfNotNull(item.kind.name, item.quality, if (item.audioOnly) "audio" else null).joinToString(" · ")) },
              trailingContent = {
                IconButton(onClick = {
                  showMedia = false
                  tabs.play(tab, item)
                }) { Icon(Icons.Filled.PlayArrow, "Play in mpvGo") }
              },
            )
          }
        }
        Spacer(Modifier.height(24.dp))
      }
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
      var rememberSite by remember(event) { mutableStateOf(true) }
      AlertDialog(
        onDismissRequest = {
          tabs.cancelAsk(event.tabId)
          ask = null
        },
        title = { Text("Play this video in mpvGo?") },
        text = {
          Column {
            Text(if (event.media != null) "mpvGo found the stream on ${event.host}." else "No stream found yet on ${event.host}; you can play it on the page.")
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { rememberSite = !rememberSite }) {
              Checkbox(checked = rememberSite, onCheckedChange = { rememberSite = it })
              Text("Remember for this site")
            }
          }
        },
        confirmButton = {
          TextButton(enabled = event.media != null, onClick = {
            tabs.answerAsk(event.tabId, event.host, SiteChoice.MPVGO, rememberSite, event.media)
            ask = null
          }) { Text("mpvGo") }
        },
        dismissButton = {
          TextButton(onClick = {
            tabs.answerAsk(event.tabId, event.host, SiteChoice.WEB, rememberSite, event.media)
            ask = null
          }) { Text("Web player") }
        },
      )
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
  StartPage(
    isPrivate = tab.isPrivate,
    engine = runCatching { SearchEngine.valueOf(engineName) }.getOrDefault(SearchEngine.GOOGLE),
    customSearchUrl = custom,
    dials = SpeedDials.decode(rawDials),
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

@Composable
private fun MediaFab(
  tab: BrowserTab,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  val media by tab.sniffer.media.collectAsState()
  val count = media.count { !it.audioOnly }
  if (count == 0) return
  FloatingActionButton(
    onClick = onClick,
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    modifier = modifier,
  ) {
    BadgedBox(badge = { Badge { Text("$count") } }) {
      Icon(Icons.Filled.SmartDisplay, if (count == 1) "1 video found" else "$count videos found")
    }
  }
}
