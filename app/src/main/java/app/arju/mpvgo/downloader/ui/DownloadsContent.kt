package app.arju.mpvgo.downloader.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.arju.mpvgo.core.settings.PreferencesScreen
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.core.utils.media.MediaFormatUtils
import app.arju.mpvgo.downloader.DownloadManager
import app.arju.mpvgo.downloader.DownloadStreamServer
import app.arju.mpvgo.downloader.LiveProgress
import app.arju.mpvgo.downloader.data.DownloadEntity
import app.arju.mpvgo.downloader.data.DownloadPreferences
import app.arju.mpvgo.downloader.data.DownloadStatus
import app.arju.mpvgo.library.components.BrowserTopBar
import app.arju.mpvgo.library.cards.VideoCard
import app.arju.mpvgo.library.filesystem.FileSystemDirectoryScreen
import app.arju.mpvgo.library.states.EmptyState
import app.arju.mpvgo.webbrowser.GoBrowserFab
import app.arju.mpvgo.webbrowser.MainFabBottomPadding
import app.arju.mpvgo.webbrowser.openGoBrowser
import app.arju.mpvgo.webbrowser.openLinkInBrowser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import java.io.File

private enum class DownloadFilter(val label: String) { All("All"), Downloading("Downloading"), Completed("Completed"), Failed("Failed") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsContent() {
  val context = LocalContext.current
  val backstack = LocalBackStack.current
  val manager = koinInject<DownloadManager>()
  val server = koinInject<DownloadStreamServer>()
  val prefs = koinInject<DownloadPreferences>()
  val scope = rememberCoroutineScope()

  val all by manager.downloads.collectAsState(initial = null)
  val live by manager.live.collectAsState()
  val waitingForWifi by manager.waitingForWifi.collectAsState()
  var filter by rememberSaveable { mutableStateOf(DownloadFilter.All) }
  var searching by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  var selected by remember { mutableStateOf(emptySet<Long>()) }
  var detailId by remember { mutableStateOf<Long?>(null) }
  var editLinkFor by remember { mutableStateOf<DownloadEntity?>(null) }
  var deleteIds by remember { mutableStateOf<Set<Long>?>(null) }
  var showBatteryPrompt by remember { mutableStateOf(false) }
  app.arju.mpvgo.library.BlockTabSwipe(selected.isNotEmpty() || searching)

  LaunchedEffect(Unit) {
    if (!prefs.batteryPromptShown.get()) {
      prefs.batteryPromptShown.set(true)
      showBatteryPrompt = !isIgnoringBatteryOptimizations(context)
    }
  }

  val items = all.orEmpty()
  val matching = if (query.isBlank()) items else items.filter {
    it.fileName.contains(query, true) || it.title?.contains(query, true) == true || it.url.contains(query, true)
  }
  val active = matching.filter { it.status.inProgress }
    .sortedWith(compareBy<DownloadEntity> { statusOrder(it.status) }.thenByDescending { it.priority }.thenBy { it.createdAt })
  val completed = matching.filter { it.status == DownloadStatus.Completed }.sortedByDescending { it.completedAt ?: it.createdAt }
  val failed = matching.filter { it.status == DownloadStatus.Failed }.sortedByDescending { it.createdAt }
  val queueOrder = active.filter { it.status == DownloadStatus.Queued }.mapIndexed { i, e -> e.id to i + 1 }.toMap()
  val visible = when (filter) {
    DownloadFilter.All -> active + failed + completed
    DownloadFilter.Downloading -> active
    DownloadFilter.Completed -> completed
    DownloadFilter.Failed -> failed
  }
  val inSelection = selected.isNotEmpty()
  val selectedEntities = items.filter { it.id in selected }

  fun toggle(id: Long) {
    selected = if (id in selected) selected - id else selected + id
  }

  Scaffold(
    topBar = {
      BrowserTopBar(
        title = "Downloads",
        isInSelectionMode = inSelection,
        selectedCount = selected.size,
        totalCount = visible.size,
        onCancelSelection = { selected = emptySet() },
        onSearchClick = {
          searching = !searching
          if (!searching) query = ""
        },
        onSettingsClick = { backstack.add(PreferencesScreen) },
        onBrowserClick = { backstack.openGoBrowser() },
        onPlayClick = {
          manager.resume(selected)
          selected = emptySet()
        },
        onPauseClick = {
          manager.pause(selectedEntities.filter { it.status.isActive }.map { it.id })
          selected = emptySet()
        },
        onShareClick = {
          shareDownloads(context, selectedEntities.filter { it.status == DownloadStatus.Completed })
          selected = emptySet()
        },
        onDeleteClick = { deleteIds = selected },
        onSelectAll = { selected = visible.map { it.id }.toSet() },
        onDeselectAll = { selected = emptySet() },
        onInvertSelection = { selected = visible.map { it.id }.toSet() - selected },
      )
    },
  ) { padding ->
    Box(Modifier.fillMaxSize().padding(padding)) {
      Column(Modifier.fillMaxSize()) {
        if (searching) {
          OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search downloads") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
          )
        }
        Row(
          Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          DownloadFilter.entries.forEach { f ->
            val count = when (f) {
              DownloadFilter.All -> items.size
              DownloadFilter.Downloading -> items.count { it.status.inProgress }
              DownloadFilter.Completed -> items.count { it.status == DownloadStatus.Completed }
              DownloadFilter.Failed -> items.count { it.status == DownloadStatus.Failed }
            }
            FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text("${f.label} $count") })
          }
          AssistChip(
            onClick = { backstack.add(DownloadPreferencesScreen) },
            label = { Text("Limits & Folder") },
            leadingIcon = { Icon(Icons.Filled.Tune, null) },
          )
        }

        if (all != null && visible.isEmpty()) {
          if (items.isEmpty()) {
            EmptyDownloads(
              onOpenBrowser = { backstack.openGoBrowser() },
              onPasteLink = { DownloadRequests.request(DownloadDraft(url = clipboardText(context).orEmpty(), forceSheet = true)) },
            )
          } else {
            EmptyState(
              icon = Icons.Filled.Download,
              title = "Nothing here",
              message = if (query.isNotBlank()) "No downloads match \"$query\"." else if (filter == DownloadFilter.All) "No downloads." else "No ${filter.label.lowercase()} downloads.",
              modifier = Modifier.fillMaxSize(),
            )
          }
        } else {
          LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp + MainFabBottomPadding),
          ) {
            fun section(title: String, list: List<DownloadEntity>, action: Pair<String, () -> Unit>?) {
              if (list.isEmpty()) return
              if (filter == DownloadFilter.All) {
                item(key = "h-$title") { SectionHeader("$title (${list.size})", action) }
              }
              items(list, key = { it.id }) { entity ->
                val click = { if (inSelection) toggle(entity.id) else onItemClick(entity, context) { detailId = it } }
                val longClick = { toggle(entity.id) }
                when (entity.status) {
                  DownloadStatus.Completed -> CompletedItem(
                    entity = entity,
                    selected = entity.id in selected,
                    onClick = click,
                    onLongClick = longClick,
                    onDelete = { deleteIds = setOf(entity.id) },
                    onShowInFolder = { path -> backstack.add(FileSystemDirectoryScreen(path)) },
                    onOpenSourcePage = { entity.sourcePageUrl?.let(backstack::openLinkInBrowser) },
                  )
                  DownloadStatus.Failed -> FailedItem(
                    entity = entity,
                    selected = entity.id in selected,
                    onClick = click,
                    onLongClick = longClick,
                    onRetry = { manager.retry(listOf(entity.id)) },
                    onEditLink = { editLinkFor = entity },
                    onRefreshLink =
                      if (app.arju.mpvgo.webbrowser.core.LinkRefresh.canRefresh(entity)) {
                        { app.arju.mpvgo.webbrowser.core.LinkRefresh.start(entity)?.let(backstack::openLinkInBrowser) }
                      } else {
                        null
                      },
                    onRemove = { manager.delete(listOf(entity.id), deleteFiles = false) },
                  )
                  else -> ActiveItem(
                    entity = entity,
                    live = live[entity.id],
                    queuePosition = queueOrder[entity.id],
                    waitingForWifi = waitingForWifi && entity.wifiOnly,
                    selected = entity.id in selected,
                    onClick = click,
                    onLongClick = longClick,
                    onPauseResume = {
                      if (entity.status == DownloadStatus.Paused) manager.resume(listOf(entity.id)) else manager.pause(listOf(entity.id))
                    },
                    onCancel = { manager.delete(listOf(entity.id), deleteFiles = true) },
                  )
                }
              }
            }
            val pauseAll: Pair<String, () -> Unit>? =
              if (active.any { it.status != DownloadStatus.Paused }) "Pause all" to { manager.pauseAll(); Unit } else null
            section("Downloading", active, pauseAll)
            section("Failed", failed, null)
            section("Completed", completed, "Clear" to { manager.delete(completed.map { it.id }, deleteFiles = false); Unit })
          }
        }
      }

      if (!inSelection) {
        GoBrowserFab(visible = true, onClick = { backstack.openGoBrowser() }, modifier = Modifier.align(Alignment.BottomStart))
        FloatingActionButton(
          onClick = { DownloadRequests.request(DownloadDraft(forceSheet = true)) },
          modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp + MainFabBottomPadding),
        ) {
          Icon(Icons.Filled.Add, contentDescription = "New download")
        }
      }
    }
  }

  detailId?.let { id ->
    val entity = items.firstOrNull { it.id == id }
    if (entity == null || !entity.status.inProgress) {
      detailId = null
    } else {
      DownloadDetailSheet(
        entity = entity,
        live = live[id],
        manager = manager,
        onDismiss = { detailId = null },
        onPlayNow = {
          detailId = null
          scope.launch { DownloadPlayback.play(context, manager, server, id) }
        },
      )
    }
  }

  editLinkFor?.let { entity ->
    var link by remember(entity.id) { mutableStateOf(entity.url) }
    AlertDialog(
      onDismissRequest = { editLinkFor = null },
      title = { Text("Edit link") },
      text = { OutlinedTextField(value = link, onValueChange = { link = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
      confirmButton = {
        TextButton(onClick = {
          manager.updateLink(entity.id, link.trim())
          editLinkFor = null
        }, enabled = link.trim().startsWith("http", true)) { Text("Save & retry") }
      },
      dismissButton = { TextButton(onClick = { editLinkFor = null }) { Text("Cancel") } },
    )
  }

  deleteIds?.let { ids ->
    val anyCompleted = items.any { it.id in ids && it.status == DownloadStatus.Completed }
    var deleteFiles by remember(ids) { mutableStateOf(prefs.deleteFileOnRemove.get()) }
    AlertDialog(
      onDismissRequest = { deleteIds = null },
      title = { Text(if (ids.size == 1) "Remove download?" else "Remove ${ids.size} downloads?") },
      text = {
        if (anyCompleted) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
            Text("Also delete the video files")
          }
        } else {
          Text("Unfinished parts are deleted.")
        }
      },
      confirmButton = {
        TextButton(onClick = {
          manager.delete(ids, deleteFiles)
          deleteIds = null
          selected = emptySet()
        }) { Text("Remove") }
      },
      dismissButton = { TextButton(onClick = { deleteIds = null }) { Text("Cancel") } },
    )
  }

  if (showBatteryPrompt) {
    AlertDialog(
      onDismissRequest = { showBatteryPrompt = false },
      title = { Text("Keep downloads running?") },
      text = { Text("Android may pause long downloads in the background to save battery. Allow mpvGo to run without battery restrictions? You can change this later in Settings › Downloads.") },
      confirmButton = {
        TextButton(onClick = {
          showBatteryPrompt = false
          openBatterySettings(context, request = true)
        }) { Text("Allow") }
      },
      dismissButton = { TextButton(onClick = { showBatteryPrompt = false }) { Text("Not now") } },
    )
  }
}

private val DownloadStatus.inProgress get() = isActive || this == DownloadStatus.Paused

private fun statusOrder(status: DownloadStatus) = when (status) {
  DownloadStatus.Running -> 0
  DownloadStatus.Queued -> 1
  else -> 2
}

private fun onItemClick(entity: DownloadEntity, context: Context, openDetail: (Long) -> Unit) {
  when (entity.status) {
    DownloadStatus.Completed -> DownloadPlayback.playCompleted(context, entity)
    DownloadStatus.Failed -> Unit
    else -> openDetail(entity.id)
  }
}

@Composable
private fun SectionHeader(title: String, action: Pair<String, () -> Unit>?) {
  Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
    action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
  }
}

@Composable
private fun EmptyDownloads(onOpenBrowser: () -> Unit, onPasteLink: () -> Unit) {
  Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
    Icon(Icons.Filled.Download, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp).height(48.dp).width(48.dp))
    Text("No downloads yet", style = MaterialTheme.typography.titleLarge)
    Text(
      "Find a video in Go Browser or paste a link.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.outline,
      modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = onOpenBrowser) { Text("Open browser") }
      OutlinedButton(onClick = onPasteLink) { Text("Paste link") }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).combinedClickable(onClick = onClick, onLongClick = onLongClick),
    colors = CardDefaults.cardColors(
      containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ),
  ) { Column(Modifier.padding(12.dp)) { content() } }
}

@Composable
private fun ActiveItem(
  entity: DownloadEntity,
  live: LiveProgress?,
  queuePosition: Int?,
  waitingForWifi: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onPauseResume: () -> Unit,
  onCancel: () -> Unit,
) {
  val downloaded = live?.downloaded ?: entity.downloadedBytes
  val total = live?.total?.takeIf { it > 0 } ?: entity.totalBytes
  val segDone = live?.segmentsDone ?: entity.segmentsDone
  val segTotal = live?.segmentsTotal?.takeIf { it > 0 } ?: entity.segmentsTotal
  val fraction = when {
    entity.isHls && segTotal > 0 -> segDone.toFloat() / segTotal
    total > 0 -> downloaded.toFloat() / total
    else -> null
  }
  ItemCard(selected, onClick, onLongClick) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(entity.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
          activeStatusLine(entity, live, queuePosition, waitingForWifi, fraction),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
        Text(
          activeDetailLine(entity, live, downloaded, total, segDone, segTotal),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
      if (entity.wifiOnly) Icon(Icons.Filled.Wifi, "Wi-Fi only", tint = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 4.dp))
      IconButton(onClick = onPauseResume) {
        if (entity.status == DownloadStatus.Paused) Icon(Icons.Filled.PlayArrow, "Resume") else Icon(Icons.Filled.Pause, "Pause")
      }
      IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") }
    }
    Spacer(Modifier.height(6.dp))
    if (fraction != null) {
      LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
    } else if (entity.status == DownloadStatus.Running) {
      LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
  }
}

private fun activeStatusLine(entity: DownloadEntity, live: LiveProgress?, queuePosition: Int?, waitingForWifi: Boolean, fraction: Float?): String {
  val pct = fraction?.let { "${(it * 100).toInt()}%" }
  return when (entity.status) {
    DownloadStatus.Paused -> listOfNotNull("Paused", pct).joinToString(" · ")
    DownloadStatus.Queued -> if (waitingForWifi) "Waiting for Wi-Fi" else "Queued #${queuePosition ?: 1}"
    else -> listOfNotNull(
      pct,
      live?.speedBps?.takeIf { it > 0 }?.let { "${MediaFormatUtils.formatFileSize(it)}/s" },
      live?.etaSeconds?.takeIf { it >= 0 }?.let { "${formatEta(it)} left" },
    ).ifEmpty { listOf("Starting…") }.joinToString(" · ")
  }
}

private fun activeDetailLine(entity: DownloadEntity, live: LiveProgress?, downloaded: Long, total: Long, segDone: Int, segTotal: Int): String =
  listOfNotNull(
    if (total > 0) "${MediaFormatUtils.formatFileSize(downloaded)} / ${MediaFormatUtils.formatFileSize(total)}" else MediaFormatUtils.formatFileSize(downloaded),
    if (entity.isHls && segTotal > 0) "seg $segDone/$segTotal" else null,
    live?.connections?.takeIf { it > 1 }?.let { "$it conn" },
  ).joinToString(" · ")

private fun formatEta(seconds: Long): String = when {
  seconds >= 3600 -> "${seconds / 3600}h ${seconds % 3600 / 60}m"
  seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
  else -> "${seconds}s"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FailedItem(
  entity: DownloadEntity,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onRetry: () -> Unit,
  onEditLink: () -> Unit,
  onRemove: () -> Unit,
  onRefreshLink: (() -> Unit)? = null,
) {
  ItemCard(selected, onClick, onLongClick) {
    Text(entity.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
    Text(
      entity.failReason?.message ?: "Download failed",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.error,
    )
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
      TextButton(onClick = onRetry) {
        Icon(Icons.Filled.Refresh, null)
        Spacer(Modifier.width(4.dp))
        Text("Retry")
      }
      if (onRefreshLink != null) {
        TextButton(onClick = onRefreshLink) { Text("Refresh link") }
      }
      TextButton(onClick = onEditLink) {
        Icon(Icons.Filled.Edit, null)
        Spacer(Modifier.width(4.dp))
        Text("Edit link")
      }
      TextButton(onClick = onRemove) { Text("Remove") }
    }
  }
}

@Composable
private fun CompletedItem(
  entity: DownloadEntity,
  selected: Boolean,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  onDelete: () -> Unit,
  onShowInFolder: (String) -> Unit,
  onOpenSourcePage: () -> Unit,
) {
  val context = LocalContext.current
  val video = remember(entity) { entity.toVideo() } ?: return
  var menu by remember { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(Modifier.weight(1f)) {
      VideoCard(video = video, onClick = onClick, onLongClick = onLongClick, isSelected = selected)
    }
    Box {
      IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
      DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
        val parent = File(video.path).parent
        buildList<Pair<String, () -> Unit>> {
          add("Play" to { DownloadPlayback.playCompleted(context, entity) })
          add("Open with" to { openWith(context, entity) })
          add("Share" to { shareDownloads(context, listOf(entity)) })
          if (parent != null) add("Show in folder" to { onShowInFolder(parent) })
          if (entity.sourcePageUrl != null && !entity.isPrivate) add("Open source page" to onOpenSourcePage)
          add("Copy link" to { copyLink(context, entity.url) })
          add("Download again" to { DownloadRequests.request(DownloadDraft.from(entity)) })
          add("Delete" to onDelete)
        }.forEach { (label, action) ->
          DropdownMenuItem(text = { Text(label) }, onClick = {
            menu = false
            action()
          })
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadDetailSheet(
  entity: DownloadEntity,
  live: LiveProgress?,
  manager: DownloadManager,
  onDismiss: () -> Unit,
  onPlayNow: () -> Unit,
) {
  var renaming by remember { mutableStateOf(false) }
  var parts by remember { mutableStateOf(manager.plan(entity.id)?.let { it.total to it.snapshot().map { p -> Triple(p.start, p.pos, p.end) } }) }
  LaunchedEffect(entity.id) {
    while (true) {
      parts = manager.plan(entity.id)?.let { it.total to it.snapshot().map { p -> Triple(p.start, p.pos, p.end) } }
      delay(500)
    }
  }
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(entity.fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, "Rename") }
      }
      parts?.let { (total, list) ->
        Text("Parts (${list.size})", style = MaterialTheme.typography.labelMedium)
        val done = MaterialTheme.colorScheme.primary
        val track = MaterialTheme.colorScheme.surfaceVariant
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
          drawRoundRect(track, cornerRadius = CornerRadius(6f, 6f))
          if (total > 0) list.forEach { (start, pos, _) ->
            val x = size.width * start / total
            val w = size.width * (pos - start) / total
            if (w > 0) drawRect(done, topLeft = Offset(x, 0f), size = Size(w, size.height))
          }
        }
      }
      DetailRow("Status", entity.status.name + (entity.failReason?.let { " (${it.message})" } ?: ""))
      val total = live?.total?.takeIf { it > 0 } ?: entity.totalBytes
      DetailRow("Size", if (total > 0) MediaFormatUtils.formatFileSize(total) else "Unknown")
      live?.speedBps?.takeIf { it > 0 }?.let { DetailRow("Speed", "${MediaFormatUtils.formatFileSize(it)}/s") }
      DetailRow("Connections", if (entity.isHls) "HLS" else "${live?.connections ?: 0} of ${entity.connections}")
      DetailRow("Folder", "/${entity.folder}")
      DetailRow("Link", entity.url)
      entity.sourcePageUrl?.let { DetailRow("Page", it) }
      Button(onClick = onPlayNow, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.PlayArrow, null)
        Spacer(Modifier.width(6.dp))
        Text("Play now in mpv")
      }
    }
  }
  if (renaming) {
    var name by remember { mutableStateOf(entity.fileName) }
    AlertDialog(
      onDismissRequest = { renaming = false },
      title = { Text("Rename") },
      text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
      confirmButton = {
        TextButton(onClick = {
          manager.rename(entity.id, name.trim())
          renaming = false
        }, enabled = name.isNotBlank()) { Text("Rename") }
      },
      dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun DetailRow(label: String, value: String) {
  Row {
    Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.width(96.dp))
    Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
  }
}

private fun contentUriOf(context: Context, entity: DownloadEntity): Uri? {
  entity.finalUri?.let { return Uri.parse(it) }
  val path = entity.finalPath ?: return null
  return runCatching { FileProvider.getUriForFile(context, "${context.packageName}.provider", File(path)) }.getOrNull()
}

private fun shareDownloads(context: Context, entities: List<DownloadEntity>) {
  val uris = entities.mapNotNull { contentUriOf(context, it) }
  if (uris.isEmpty()) return
  val intent = if (uris.size == 1) {
    Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
  } else {
    Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
  }
  intent.type = "video/*"
  intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  context.startActivity(Intent.createChooser(intent, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun openWith(context: Context, entity: DownloadEntity) {
  val uri = contentUriOf(context, entity) ?: return
  val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, entity.mimeType ?: "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  runCatching { context.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun copyLink(context: Context, url: String) {
  context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Link", url))
}
