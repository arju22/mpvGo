package app.arju.mpvgo.webbrowser.ui

import app.arju.mpvgo.webbrowser.core.UserAgentPreset
import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.arju.mpvgo.webbrowser.adblock.SiteMatcher
import app.arju.mpvgo.webbrowser.core.FaviconStore
import org.koin.compose.koinInject
import app.arju.mpvgo.webbrowser.core.BrowserTab
import app.arju.mpvgo.webbrowser.core.SiteChoice
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

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
      val context = LocalContext.current
      var memInfo by remember { mutableStateOf<Pair<Long, Long>?>(null) }
      LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mi = ActivityManager.MemoryInfo()
        while (isActive) {
          am?.getMemoryInfo(mi)
          if (mi.totalMem > 0L) {
            memInfo = Pair(mi.totalMem - mi.availMem, mi.totalMem)
          }
          delay(2000)
        }
      }

      Row(
        Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        // Normal tabs button
        val normalBg = if (!showPrivate) {
          if (showPrivate) Color(0xFF3B2A66) else Color(0xFF4A4458)
        } else {
          Color.Transparent
        }
        Box(
          modifier = Modifier
            .size(40.dp)
            .background(normalBg, RoundedCornerShape(10.dp))
            .clickable { onShowPrivate(false) },
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier
              .size(24.dp)
              .border(
                width = 2.dp,
                color = if (showPrivate) Color(0xFFE6E0E9) else MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(6.dp),
              ),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = "${normal.size}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
              color = if (showPrivate) Color(0xFFE6E0E9) else MaterialTheme.colorScheme.onSurface,
            )
          }
        }

        Spacer(Modifier.width(4.dp))

        // Private tabs button
        val privateBg = if (showPrivate) Color(0xFF3B2A66) else Color.Transparent
        Box(
          modifier = Modifier
            .size(40.dp)
            .background(privateBg, RoundedCornerShape(10.dp))
            .clickable {
              if (private.isEmpty()) {
                onNewPrivateTab()
              } else {
                onShowPrivate(true)
              }
            },
          contentAlignment = Alignment.Center,
        ) {
          Box(
            modifier = Modifier.size(24.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(Modifier.fillMaxSize()) {
              drawRoundRect(
                color = Color(0xFFC9B6FF),
                style = Stroke(
                  width = 2.dp.toPx(),
                  pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
                ),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
              )
            }
            Text(
              text = "\uD83D\uDD76",
              style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
              modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-9).dp),
            )
            Text(
              text = "${private.size}",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
              color = Color(0xFFC9B6FF),
            )
          }
        }

        Spacer(Modifier.width(6.dp))

        // RAM Indicator
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp),
        ) {
          val (usedBytes, totalBytes) = memInfo ?: Pair(0L, 0L)
          val usedGb = usedBytes.toDouble() / (1024 * 1024 * 1024)
          val totalGb = totalBytes.toDouble() / (1024 * 1024 * 1024)
          val usedFraction = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
          val totalDisplay = if (abs(totalGb - round(totalGb)) < 0.25) {
            String.format(Locale.US, "%.0f", totalGb)
          } else {
            String.format(Locale.US, "%.1f", totalGb)
          }
          val usedTotalText = if (totalBytes > 0) {
            String.format(Locale.US, "%.1f / %s GB", usedGb, totalDisplay)
          } else {
            "-- / -- GB"
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text("RAM", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFFCAC4D0))
            Text(usedTotalText, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = Color(0xFFCAC4D0))
          }
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp)
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(Color(0xFF49454F)),
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth(fraction = usedFraction)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFD0BCFF)),
            )
          }
        }

        IconButton(enabled = list.isNotEmpty(), onClick = onCloseAll) {
          Icon(Icons.Filled.DeleteSweep, if (showPrivate) "Close all private tabs" else "Close all tabs")
        }
      }

      // Navigation row under drawer header
      Row(
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp)
          .height(52.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(if (showPrivate) Color(0xFF28194A) else Color(0xFF2B2930)),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        IconButton(onClick = onExit, modifier = Modifier.size(44.dp)) {
          Icon(
            Icons.AutoMirrored.Filled.ExitToApp,
            "Close browser",
            tint = if (showPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(enabled = canGoBack, onClick = onBack, modifier = Modifier.size(44.dp)) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            "Back",
            tint = if (showPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(onClick = onHome, modifier = Modifier.size(44.dp)) {
          Icon(
            Icons.Filled.Home,
            "Home",
            tint = if (showPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(enabled = canGoForward, onClick = onForward, modifier = Modifier.size(44.dp)) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            "Forward",
            tint = if (showPrivate) PrivateColors.content else MaterialTheme.colorScheme.onSurface,
          )
        }
        IconButton(
          onClick = { if (showPrivate) onNewPrivateTab() else onNewTab() },
          modifier = Modifier.size(44.dp),
        ) {
          Icon(
            Icons.Filled.Add,
            if (showPrivate) "New private tab" else "New tab",
            tint = if (showPrivate) PrivateColors.accent else MaterialTheme.colorScheme.onSurface,
          )
        }
      }
      Spacer(Modifier.height(6.dp))

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
      LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(top = 4.dp)) {
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
      val faviconStore = koinInject<FaviconStore>()
      val cachedIcon = remember(url) { if (!isPrivate && url.isNotEmpty()) faviconStore.get(SiteMatcher.host(url)) else null }
      val bmp = icon ?: cachedIcon
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

/** Per-site settings: ad block, pop-ups, video tap and user agent. */
@Composable
fun SiteSettingsContent(
  host: String,
  blocked: Int,
  adBlockOn: Boolean,
  blockPopups: Boolean,
  videoChoice: SiteChoice?,
  uaPreset: UserAgentPreset?,
  globalPreset: UserAgentPreset,
  onAdBlock: (Boolean) -> Unit,
  onBlockPopups: (Boolean) -> Unit,
  onVideoChoice: (SiteChoice?) -> Unit,
  onUaPreset: (UserAgentPreset?) -> Unit,
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
    listOf(null to "Play in browser (default)", SiteChoice.MPVGO to "Play in mpvGo", SiteChoice.ASK to "Ask me").forEach { (choice, label) ->
      Row(
        Modifier.fillMaxWidth().selectable(videoChoice == choice, role = Role.RadioButton) { onVideoChoice(choice) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = videoChoice == choice, onClick = null, modifier = Modifier.padding(12.dp))
        Text(label)
      }
    }
    Spacer(Modifier.height(8.dp))
    Text("User agent for this site", style = MaterialTheme.typography.titleSmall)
    listOf(
      null to "Default (${globalPreset.displayName})",
      UserAgentPreset.PHONE to "Phone",
      UserAgentPreset.TABLET to "Tablet",
      UserAgentPreset.DESKTOP to "Desktop",
    ).forEach { (preset, label) ->
      Row(
        Modifier.fillMaxWidth().selectable(uaPreset == preset, role = Role.RadioButton) { onUaPreset(preset) },
        verticalAlignment = Alignment.CenterVertically,
      ) {
        RadioButton(selected = uaPreset == preset, onClick = null, modifier = Modifier.padding(12.dp))
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
