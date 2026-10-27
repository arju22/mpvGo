package app.arju.mpvgo.webbrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arju.mpvgo.webbrowser.core.TopBarButton

/** Everything the browser chrome can ask the screen to do. */
enum class BrowserAction {
  TABS,
  RELOAD,
  STOP,
  FORWARD,
  BOOKMARK_PAGE,
  SHARE,
  NEW_TAB,
  NEW_PRIVATE_TAB,
  BOOKMARKS,
  HISTORY,
  DOWNLOADS,
  FIND,
  AD_BLOCK,
  DESKTOP,
  SITE_SETTINGS,
  SETTINGS,
  CLOSE_BROWSER,
}

/** State the top bar shows; a plain snapshot so the bar stays stateless. */
data class TopBarState(
  val url: String,
  val isPrivate: Boolean,
  val loading: Boolean,
  val progress: Int,
  val tabCount: Int,
  val canGoForward: Boolean,
  val buttons: List<TopBarButton>,
  val activeDownloads: Int,
  val adBlockOnSite: Boolean,
  val desktop: Boolean,
  val bookmarked: Boolean = false,
)

val TopBarHeight = 56.dp

@Composable
fun BrowserTopBar(
  state: TopBarState,
  onAction: (BrowserAction) -> Unit,
  onSubmit: (String) -> Unit,
  onSwipeTab: (Int) -> Unit,
  editRequest: Int,
) {
  val container = if (state.isPrivate) PrivateColors.background else MaterialTheme.colorScheme.surface
  val content = if (state.isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface
  var editing by remember { mutableStateOf(false) }
  LaunchedEffect(editRequest) { if (editRequest > 0) editing = true }

  Surface(color = container, contentColor = content) {
    Column {
      Box(Modifier.fillMaxWidth().height(TopBarHeight)) {
        if (editing) {
          UrlEditor(
            initial = state.url,
            isPrivate = state.isPrivate,
            onCancel = { editing = false },
            onSubmit = {
              editing = false
              onSubmit(it)
            },
          )
        } else {
          Row(Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TabsButton(state.tabCount, state.isPrivate) { onAction(BrowserAction.TABS) }
            UrlBox(
              state = state,
              onClick = { editing = true },
              onReload = { onAction(if (state.loading) BrowserAction.STOP else BrowserAction.RELOAD) },
              onSwipeTab = onSwipeTab,
              modifier = Modifier.weight(1f),
            )
            state.buttons.forEach { button ->
              when (button) {
                TopBarButton.DOWNLOADS ->
                  IconButton(onClick = { onAction(BrowserAction.DOWNLOADS) }) {
                    BadgedBox(badge = { if (state.activeDownloads > 0) Badge { Text("${state.activeDownloads}") } }) {
                      Icon(Icons.Filled.Download, "Downloads")
                    }
                  }
                else -> IconButton(onClick = { onAction(button.action()) }) { Icon(button.icon(), button.label) }
              }
            }
            OverflowMenu(state, onAction)
          }
        }
      }
      // Fixed 2dp slot so the bar never changes height while loading.
      Box(Modifier.fillMaxWidth().height(2.dp)) {
        if (state.loading) {
          LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
        }
      }
    }
  }
}

fun TopBarButton.action(): BrowserAction =
  when (this) {
    TopBarButton.BOOKMARKS -> BrowserAction.BOOKMARKS
    TopBarButton.DOWNLOADS -> BrowserAction.DOWNLOADS
    TopBarButton.HISTORY -> BrowserAction.HISTORY
    TopBarButton.NEW_TAB -> BrowserAction.NEW_TAB
    TopBarButton.SHARE -> BrowserAction.SHARE
    TopBarButton.FIND -> BrowserAction.FIND
  }

fun TopBarButton.icon(): ImageVector =
  when (this) {
    TopBarButton.BOOKMARKS -> Icons.Filled.Bookmarks
    TopBarButton.DOWNLOADS -> Icons.Filled.Download
    TopBarButton.HISTORY -> Icons.Filled.History
    TopBarButton.NEW_TAB -> Icons.Filled.Add
    TopBarButton.SHARE -> Icons.Filled.Share
    TopBarButton.FIND -> Icons.Filled.Search
  }

@Composable
private fun TabsButton(
  count: Int,
  isPrivate: Boolean,
  onClick: () -> Unit,
) {
  IconButton(onClick = onClick) {
    val color = if (isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface
    Box(
      Modifier.size(24.dp).border(2.dp, color, RoundedCornerShape(6.dp)),
      contentAlignment = Alignment.Center,
    ) {
      if (isPrivate && count == 0) {
        Icon(Icons.Filled.VisibilityOff, null, Modifier.size(14.dp), tint = color)
      } else {
        Text(if (count > 99) "∞" else "$count", style = MaterialTheme.typography.labelMedium, color = color)
      }
    }
  }
}

@Composable
private fun UrlBox(
  state: TopBarState,
  onClick: () -> Unit,
  onReload: () -> Unit,
  onSwipeTab: (Int) -> Unit,
  modifier: Modifier,
) {
  val threshold = with(LocalDensity.current) { 64.dp.toPx() }
  var drag by remember { mutableFloatStateOf(0f) }
  val secure = state.url.startsWith("https://")
  Surface(
    shape = RoundedCornerShape(22.dp),
    color = if (state.isPrivate) PrivateColors.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
    modifier =
      modifier
        .height(44.dp)
        .pointerInput(Unit) {
          detectHorizontalDragGestures(
            onDragStart = { drag = 0f },
            onDragEnd = {
              if (drag <= -threshold) onSwipeTab(1) else if (drag >= threshold) onSwipeTab(-1)
              drag = 0f
            },
            onDragCancel = { drag = 0f },
          ) { change, amount ->
            change.consume()
            drag += amount
          }
        }.clickable(onClick = onClick),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
      when {
        state.isPrivate -> Icon(Icons.Filled.VisibilityOff, "Private tab", Modifier.size(16.dp))
        state.url.isEmpty() -> Icon(Icons.Filled.Search, null, Modifier.size(16.dp))
        secure -> Icon(Icons.Filled.Lock, "Secure", Modifier.size(16.dp))
        else -> Icon(Icons.Filled.Info, "Not secure", Modifier.size(16.dp))
      }
      Text(
        text = if (state.url.isEmpty()) (if (state.isPrivate) "Search privately" else "Search or type a link") else displayUrl(state.url),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
        color = LocalContentColorOr(state).copy(alpha = if (state.url.isEmpty()) 0.6f else 1f),
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
      )
      if (state.url.isNotEmpty()) {
        IconButton(onClick = onReload, modifier = Modifier.size(40.dp)) {
          Icon(if (state.loading) Icons.Filled.Close else Icons.Filled.Refresh, if (state.loading) "Stop" else "Reload", Modifier.size(20.dp))
        }
      } else {
        Box(Modifier.width(12.dp))
      }
    }
  }
}

@Composable
private fun LocalContentColorOr(state: TopBarState): Color =
  if (state.isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface

/** "https://www.example.com/a?b" → "example.com/a?b". */
fun displayUrl(url: String): String =
  url
    .substringAfter("://")
    .removePrefix("www.")
    .removeSuffix("/")

@Composable
private fun UrlEditor(
  initial: String,
  isPrivate: Boolean,
  onCancel: () -> Unit,
  onSubmit: (String) -> Unit,
) {
  var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  BackHandler(onBack = onCancel)
  val fg = if (isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface
  Row(Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel") }
    Surface(
      shape = RoundedCornerShape(22.dp),
      color = if (isPrivate) PrivateColors.surface else MaterialTheme.colorScheme.surfaceContainerHigh,
      modifier = Modifier.weight(1f).height(44.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
        BasicTextField(
          value = value,
          onValueChange = { value = it },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyMedium.copy(color = fg),
          cursorBrush = SolidColor(fg),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
          keyboardActions = KeyboardActions(onGo = { if (value.text.isNotBlank()) onSubmit(value.text) else onCancel() }),
          modifier = Modifier.weight(1f).focusRequester(focus),
        )
        if (value.text.isNotEmpty()) {
          IconButton(onClick = { value = TextFieldValue("") }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Close, "Clear", Modifier.size(20.dp))
          }
        }
      }
    }
    Box(Modifier.width(8.dp))
  }
}

@Composable
private fun OverflowMenu(
  state: TopBarState,
  onAction: (BrowserAction) -> Unit,
) {
  var open by remember { mutableStateOf(false) }
  Box {
    IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, "More") }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
      val pick = { action: BrowserAction ->
        open = false
        onAction(action)
      }
      Row(Modifier.padding(horizontal = 4.dp)) {
        IconButton(enabled = state.canGoForward, onClick = { pick(BrowserAction.FORWARD) }) {
          Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
        }
        IconButton(enabled = state.url.isNotEmpty(), onClick = { pick(BrowserAction.BOOKMARK_PAGE) }) {
          if (state.bookmarked) {
            Icon(Icons.Filled.Star, "Edit bookmark", tint = MaterialTheme.colorScheme.primary)
          } else {
            Icon(Icons.Filled.StarBorder, "Bookmark this page")
          }
        }
        IconButton(enabled = state.url.isNotEmpty(), onClick = { pick(BrowserAction.SHARE) }) { Icon(Icons.Filled.Share, "Share") }
        IconButton(onClick = { pick(BrowserAction.NEW_TAB) }) { Icon(Icons.Filled.Add, "New tab") }
      }
      HorizontalDivider()
      MenuItem("New private tab", Icons.Filled.VisibilityOff) { pick(BrowserAction.NEW_PRIVATE_TAB) }
      MenuItem("Bookmarks", Icons.Filled.Bookmarks) { pick(BrowserAction.BOOKMARKS) }
      MenuItem("History", Icons.Filled.History) { pick(BrowserAction.HISTORY) }
      MenuItem(
        if (state.activeDownloads > 0) "Downloads (${state.activeDownloads})" else "Downloads",
        Icons.Filled.Download,
      ) { pick(BrowserAction.DOWNLOADS) }
      MenuItem("Find in page", Icons.Filled.Search, enabled = state.url.isNotEmpty()) { pick(BrowserAction.FIND) }
      MenuItem("Ad block", Icons.Filled.Shield, checked = state.adBlockOnSite, enabled = state.url.isNotEmpty()) {
        pick(BrowserAction.AD_BLOCK)
      }
      MenuItem("Desktop site", Icons.Filled.DesktopWindows, checked = state.desktop) { pick(BrowserAction.DESKTOP) }
      MenuItem("Site settings", Icons.Filled.Tune, enabled = state.url.isNotEmpty()) { pick(BrowserAction.SITE_SETTINGS) }
      MenuItem("Browser settings", Icons.Filled.Settings) { pick(BrowserAction.SETTINGS) }
      HorizontalDivider()
      MenuItem("Close browser", Icons.AutoMirrored.Filled.ExitToApp) { pick(BrowserAction.CLOSE_BROWSER) }
    }
  }
}

@Composable
private fun MenuItem(
  label: String,
  icon: ImageVector,
  checked: Boolean? = null,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  DropdownMenuItem(
    text = { Text(label) },
    leadingIcon = { Icon(icon, null) },
    trailingIcon = { if (checked == true) Icon(Icons.Filled.Check, "On") },
    enabled = enabled,
    onClick = onClick,
  )
}

@Composable
fun FindBar(
  isPrivate: Boolean,
  matches: Pair<Int, Int>,
  onQuery: (String) -> Unit,
  onNext: (Boolean) -> Unit,
  onClose: () -> Unit,
) {
  var text by remember { mutableStateOf("") }
  val focus = remember { FocusRequester() }
  LaunchedEffect(Unit) { focus.requestFocus() }
  val fg = if (isPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface
  Surface(color = if (isPrivate) PrivateColors.background else MaterialTheme.colorScheme.surface, contentColor = fg) {
    Row(Modifier.fillMaxWidth().height(TopBarHeight + 2.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close find") }
      Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
        if (text.isEmpty()) Text("Find in page", color = fg.copy(alpha = 0.6f))
        BasicTextField(
          value = text,
          onValueChange = {
            text = it
            onQuery(it)
          },
          singleLine = true,
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg),
          cursorBrush = SolidColor(fg),
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { onNext(true) }),
          modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
      }
      if (text.isNotEmpty()) {
        Text(
          if (matches.second == 0) "0/0" else "${matches.first + 1}/${matches.second}",
          style = MaterialTheme.typography.labelMedium,
          modifier = Modifier.padding(horizontal = 4.dp).background(Color.Transparent),
        )
      }
      IconButton(enabled = matches.second > 0, onClick = { onNext(false) }) { Icon(Icons.Filled.KeyboardArrowUp, "Previous") }
      IconButton(enabled = matches.second > 0, onClick = { onNext(true) }) { Icon(Icons.Filled.KeyboardArrowDown, "Next") }
    }
  }
}
