package app.arju.mpvgo.webbrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import app.arju.mpvgo.webbrowser.core.BrowserTab
import app.arju.mpvgo.webbrowser.core.SiteChoice

@Composable
fun TabsDrawer(
  normal: List<BrowserTab>,
  private: List<BrowserTab>,
  activeId: Long?,
  showPrivate: Boolean,
  canGoBack: Boolean,
  canGoForward: Boolean,
  onShowPrivate: (Boolean) -> Unit,
  onSelect: (BrowserTab) -> Unit,
  onClose: (BrowserTab) -> Unit,
  onCloseAll: () -> Unit,
  onExit: () -> Unit,
  onBack: () -> Unit,
  onHome: () -> Unit,
  onForward: () -> Unit,
  onNewTab: () -> Unit,
  onNewPrivateTab: () -> Unit = onNewTab,
) {
  val list = if (showPrivate) private else normal
  ModalDrawerSheet(
    drawerContainerColor = if (showPrivate) PrivateColors.background else MaterialTheme.colorScheme.surfaceContainerLow,
    drawerContentColor = if (showPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface,
    modifier = Modifier.width(320.dp),
  ) {
    Column(Modifier.fillMaxHeight().statusBarsPadding().navigationBarsPadding()) {
      Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val segColors =
          if (showPrivate) {
            SegmentedButtonDefaults.colors(
              inactiveContainerColor = PrivateColors.background,
              inactiveContentColor = PrivateColors.content,
              inactiveBorderColor = PrivateColors.muted,
              activeBorderColor = PrivateColors.muted,
            )
          } else {
            SegmentedButtonDefaults.colors()
          }
        val normalActiveIndex = normal.indexOfFirst { it.id == activeId }
        val normalLabel = if (normalActiveIndex >= 0 && normal.isNotEmpty()) {
          "Tabs ${normalActiveIndex + 1}/${normal.size}"
        } else {
          "Tabs ${normal.size}"
        }
        val privateActiveIndex = private.indexOfFirst { it.id == activeId }
        val privateLabel = if (privateActiveIndex >= 0 && private.isNotEmpty()) {
          "Private ${privateActiveIndex + 1}/${private.size}"
        } else {
          "Private ${private.size}"
        }
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
          SegmentedButton(
            selected = !showPrivate,
            onClick = { onShowPrivate(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            colors = segColors,
          ) { Text(normalLabel) }
          SegmentedButton(
            selected = showPrivate,
            onClick = {
              if (private.isEmpty()) {
                onNewPrivateTab()
              } else {
                onShowPrivate(true)
              }
            },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            colors = segColors,
          ) { Text(privateLabel) }
        }
        IconButton(enabled = list.isNotEmpty(), onClick = onCloseAll) {
          Icon(Icons.Filled.DeleteSweep, if (showPrivate) "Close all private tabs" else "Close all tabs")
        }
      }
      if (showPrivate) {
        Text(
          "History, cookies and site data are deleted when you close private tabs.",
          style = MaterialTheme.typography.bodySmall,
          color = PrivateColors.muted,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }
      val listState = rememberLazyListState()
      androidx.compose.runtime.LaunchedEffect(activeId, showPrivate) {
        val idx = list.indexOfFirst { it.id == activeId }
        if (idx >= 0) {
          listState.scrollToItem(idx)
        }
      }
      LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 8.dp)) {
        itemsIndexed(list, key = { _, tab -> tab.id }) { index, tab ->
          TabRow(tab, tab.id == activeId, showPrivate, index, list.size, { onSelect(tab) }, { onClose(tab) })
        }
        if (list.isEmpty()) {
          item {
            Text(
              if (showPrivate) "No private tabs" else "No tabs",
              style = MaterialTheme.typography.bodyMedium,
              modifier = Modifier.padding(24.dp),
            )
          }
        }
      }
      HorizontalDivider()
      Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onExit, modifier = Modifier.size(52.dp)) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Close browser") }
        IconButton(enabled = canGoBack, onClick = onBack, modifier = Modifier.size(52.dp)) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
        }
        IconButton(onClick = onHome, modifier = Modifier.size(52.dp)) { Icon(Icons.Filled.Home, "Home") }
        IconButton(enabled = canGoForward, onClick = onForward, modifier = Modifier.size(52.dp)) {
          Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
        }
        IconButton(onClick = onNewTab, modifier = Modifier.size(52.dp)) {
          Icon(Icons.Filled.Add, if (showPrivate) "New private tab" else "New tab")
        }
      }
    }
  }
}

@Composable
private fun TabRow(
  tab: BrowserTab,
  active: Boolean,
  isPrivate: Boolean,
  index: Int,
  totalCount: Int,
  onClick: () -> Unit,
  onClose: () -> Unit,
) {
  val title by tab.title.collectAsState()
  val url by tab.url.collectAsState()
  val icon by tab.favicon.collectAsState()
  val highlight =
    when {
      !active -> androidx.compose.ui.graphics.Color.Transparent
      isPrivate -> PrivateColors.surface
      else -> MaterialTheme.colorScheme.secondaryContainer
    }
  Row(
    Modifier
      .fillMaxWidth()
      .padding(horizontal = 8.dp, vertical = 2.dp)
      .background(highlight, RoundedCornerShape(12.dp))
      .clickable(
        onClick = onClick,
        onClickLabel = "Select tab ${index + 1} of $totalCount",
      )
      .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
      val bmp = icon
      if (bmp != null) {
        Image(bmp.asImageBitmap(), null, Modifier.size(20.dp))
      } else {
        Icon(if (isPrivate) Icons.Filled.VisibilityOff else Icons.Filled.Language, null, Modifier.size(20.dp))
      }
    }
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Text(
        title.ifBlank { if (url.isEmpty()) "New tab" else SiteMatcher.host(url) },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (url.isNotEmpty()) {
        Text(SiteMatcher.host(url), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close tab") }
  }
}

/** Per-site settings: ad block, pop-ups and what a video tap does. */
@Composable
fun SiteSettingsContent(
  host: String,
  blocked: Int,
  adBlockOn: Boolean,
  blockPopups: Boolean,
  videoChoice: SiteChoice?,
  onAdBlock: (Boolean) -> Unit,
  onBlockPopups: (Boolean) -> Unit,
  onVideoChoice: (SiteChoice?) -> Unit,
) {
  Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
    Text(host, style = MaterialTheme.typography.titleMedium)
    Text(
      if (blocked == 1) "1 ad or tracker blocked on this page" else "$blocked ads and trackers blocked on this page",
      style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(12.dp))
    SwitchRow("Ad block on this site", adBlockOn, onAdBlock)
    SwitchRow("Block pop-ups (all sites)", blockPopups, onBlockPopups)
    Spacer(Modifier.height(8.dp))
    Text("When I tap a video", style = MaterialTheme.typography.titleSmall)
    listOf(null to "Ask me", SiteChoice.MPVGO to "Play in mpvGo", SiteChoice.WEB to "Play on the page").forEach { (choice, label) ->
      Row(
        Modifier.fillMaxWidth().selectable(videoChoice == choice, role = Role.RadioButton) { onVideoChoice(choice) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = videoChoice == choice, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label)
      }
    }
  }
}

@Composable
private fun SwitchRow(
  label: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, Modifier.weight(1f))
    Switch(checked = checked, onCheckedChange = onChange)
  }
}
