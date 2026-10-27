package app.arju.mpvgo.webbrowser

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.webbrowser.core.BrowserTab
import app.arju.mpvgo.webbrowser.core.DetectedMedia
import app.arju.mpvgo.webbrowser.core.MediaSniffer
import app.arju.mpvgo.webbrowser.core.SiteChoice
import app.arju.mpvgo.webbrowser.core.TabManager
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Go Browser. Phase 6: functional core; the full browser chrome lands in phase 7. */
@Serializable
data class GoBrowserScreen(val url: String? = null) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val context = LocalContext.current
    val tabs = koinInject<TabManager>()
    val tabList by tabs.tabs.collectAsState()
    val activeId by tabs.activeId.collectAsState()
    val tab = tabList.firstOrNull { it.id == activeId }

    var ask by remember { mutableStateOf<TabManager.Event.Ask?>(null) }
    var showMedia by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf<Pair<android.view.View, WebChromeClient.CustomViewCallback>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

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

    val webView = tab?.let { tabs.webViewFor(it) }
    val canGoBack by (tab?.canGoBack ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    BackHandler {
      when {
        fullscreen != null -> fullscreen?.second?.onCustomViewHidden()
        canGoBack -> webView?.goBack()
        else -> backstack.removeLastOrNull()
      }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
      Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        if (tab != null) AddressBar(tab, tabs, tabList.size, onClose = { backstack.removeLastOrNull() })
        Box(Modifier.fillMaxWidth().weight(1f)) {
          if (tab != null && webView != null) {
            val tabUrl by tab.url.collectAsState()
            val startPage = tabUrl.isEmpty()
            if (startPage) {
              StartPage(onOpen = { tabs.load(tab, it) })
            }
            AndroidView(
              factory = { FrameLayout(it) },
              update = { frame ->
                if (webView.parent !== frame) {
                  (webView.parent as? ViewGroup)?.removeView(webView)
                  frame.removeAllViews()
                  frame.addView(webView, FrameLayout.LayoutParams(-1, -1))
                }
              },
              modifier = if (startPage) Modifier.height(0.dp) else Modifier.fillMaxSize(),
            )
            MediaChip(tab.sniffer, Modifier.align(Alignment.BottomStart).padding(16.dp)) { showMedia = true }
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
private fun AddressBar(
  tab: BrowserTab,
  tabs: TabManager,
  tabCount: Int,
  onClose: () -> Unit,
) {
  val url by tab.url.collectAsState()
  val progress by tab.progress.collectAsState()
  val loading by tab.loading.collectAsState()
  val blocked by tab.blocked.collectAsState()
  val focus = LocalFocusManager.current
  var text by remember(tab.id) { mutableStateOf(url) }
  var editing by remember(tab.id) { mutableStateOf(false) }
  LaunchedEffect(url, editing) { if (!editing) text = url }
  Column {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
      IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close browser") }
      if (tab.isPrivate) Icon(Icons.Filled.VisibilityOff, "Private tab", Modifier.padding(end = 4.dp))
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        placeholder = { Text("Search or type a link") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
        keyboardActions =
          KeyboardActions(onGo = {
            if (text.isNotBlank()) tabs.load(tab, text)
            focus.clearFocus()
          }),
        modifier = Modifier.weight(1f).onFocusChanged { editing = it.isFocused },
      )
      if (blocked > 0) Text("$blocked", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
      IconButton(onClick = { tabs.webViewFor(tab).reload() }) { Icon(Icons.Filled.Refresh, "Reload") }
      TextButton(onClick = { tabs.newTab(private = !tab.isPrivate) }) { Text(if (tab.isPrivate) "+ Tab" else "+ Private") }
      if (tabCount > 1) {
        IconButton(onClick = { tabs.close(tab.id) }) { Icon(Icons.Filled.Close, "Close tab ($tabCount open)") }
      }
    }
    if (loading) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun MediaChip(
  sniffer: MediaSniffer,
  modifier: Modifier,
  onClick: () -> Unit,
) {
  val media by sniffer.media.collectAsState()
  val video = media.filterNot(DetectedMedia::audioOnly)
  if (video.isEmpty()) return
  Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp, shadowElevation = 6.dp, modifier = modifier) {
    AssistChip(
      onClick = onClick,
      label = { Text(if (video.size == 1) "1 video found" else "${video.size} videos found") },
      leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
    )
  }
}

@Composable
private fun StartPage(onOpen: (String) -> Unit) {
  Column(
    Modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(Icons.Filled.Language, null, Modifier.width(64.dp).height(64.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(16.dp))
    Text("Go Browser", style = MaterialTheme.typography.headlineSmall)
    Text("Search or type a link above.", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(16.dp))
    TextButton(onClick = { onOpen("https://duckduckgo.com") }) { Text("DuckDuckGo") }
  }
}
