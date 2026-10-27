package app.arju.mpvgo.downloader.ui

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.arju.mpvgo.downloader.DownloadManager
import app.arju.mpvgo.downloader.DownloadStreamServer
import app.arju.mpvgo.downloader.NewDownload
import app.arju.mpvgo.downloader.data.DownloadPreferences
import app.arju.mpvgo.downloader.engine.DownloadNaming
import app.arju.mpvgo.downloader.engine.HlsPlaylist
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Shows the New download sheet (or queues straight away) whenever [DownloadRequests] has a draft. */
@Composable
fun DownloadRequestHost() {
  val draft by DownloadRequests.pending.collectAsState()
  val context = LocalContext.current
  val manager = koinInject<DownloadManager>()
  val server = koinInject<DownloadStreamServer>()
  val prefs = koinInject<DownloadPreferences>()
  val scope = rememberCoroutineScope()
  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

  fun start(request: NewDownload, playNow: Boolean) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    DownloadRequests.dismiss()
    scope.launch {
      val id = manager.enqueue(request)
      if (playNow) DownloadPlayback.play(context, manager, server, id)
    }
  }

  val current = draft ?: return
  if (!current.forceSheet && !prefs.askBeforeEveryDownload.get() && current.url.isNotBlank()) {
    LaunchedEffect(current) {
      start(current.toNewDownload(), playNow = false)
    }
    return
  }
  NewDownloadSheet(
    draft = current,
    prefs = prefs,
    manager = manager,
    onDismiss = DownloadRequests::dismiss,
    onConfirm = ::start,
  )
}

private fun DownloadDraft.toRequest(fileName: String, isHls: Boolean) = toNewDownload(fileName, isHls)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NewDownloadSheet(
  draft: DownloadDraft,
  prefs: DownloadPreferences,
  manager: DownloadManager,
  onDismiss: () -> Unit,
  onConfirm: (NewDownload, Boolean) -> Unit,
) {
  val context = LocalContext.current
  var url by remember { mutableStateOf(draft.url) }
  var name by remember { mutableStateOf("") }
  var nameEdited by remember { mutableStateOf(false) }
  var folder by remember { mutableStateOf(prefs.folder.get()) }
  var connections by remember { mutableFloatStateOf(prefs.connectionsPerFile.get().toFloat()) }
  var wifiOnly by remember { mutableStateOf(prefs.wifiOnly.get()) }
  var playNow by remember { mutableStateOf(false) }
  var quality by remember { mutableIntStateOf(0) }
  var variants by remember { mutableStateOf<List<HlsPlaylist.Variant>>(emptyList()) }
  val trimmed = url.trim()
  val valid = trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)
  val isHls = valid && DownloadNaming.isHlsUrl(trimmed, draft.mimeType)

  LaunchedEffect(trimmed) {
    if (!nameEdited) name = if (valid) draft.suggestName(trimmed, isHls) else ""
  }
  LaunchedEffect(trimmed, isHls) {
    variants = emptyList()
    quality = 0
    if (isHls) {
      val headers = draft.toRequest("", true).let { req ->
        buildMap {
          req.userAgent?.let { put("User-Agent", it) }
          req.referer?.let { put("Referer", it) }
          req.cookies?.let { put("Cookie", it) }
        }
      }
      variants = runCatching { manager.hlsVariants(trimmed, headers) }.getOrDefault(emptyList())
        .filter { it.height > 0 }.distinctBy { it.height }.sortedByDescending { it.height }
    }
  }

  fun confirm(startNow: Boolean) {
    val fileName = DownloadNaming.sanitize(name.ifBlank { draft.suggestName(trimmed, isHls) })
    onConfirm(
      draft.copy(url = trimmed).toRequest(fileName, isHls).copy(
        folder = folder,
        hlsQuality = quality,
        connections = connections.toInt(),
        wifiOnly = wifiOnly,
        nameEdited = nameEdited,
        startNow = startNow,
      ),
      playNow && startNow,
    )
  }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Text("New download", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium)
      OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Link") },
        singleLine = true,
        isError = url.isNotBlank() && !valid,
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
          IconButton(onClick = { clipboardText(context)?.let { url = it.trim() } }) {
            Icon(Icons.Filled.ContentPaste, contentDescription = "Paste")
          }
        },
      )
      OutlinedTextField(
        value = name,
        onValueChange = {
          name = it
          nameEdited = true
        },
        label = { Text("Name") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      LabeledRow("Folder") {
        var open by remember { mutableStateOf(false) }
        Box {
          TextButton(onClick = { open = true }) {
            Text("/$folder", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, null)
          }
          DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DownloadPreferences.FOLDER_CHOICES.forEach {
              DropdownMenuItem(text = { Text("/$it") }, onClick = {
                folder = it
                open = false
              })
            }
          }
        }
      }
      if (isHls) {
        Text("Quality", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = quality == 0, onClick = { quality = 0 }, label = { Text("Best") })
          variants.forEach { v ->
            FilterChip(selected = quality == v.height, onClick = { quality = v.height }, label = { Text(v.label) })
          }
        }
      } else {
        Column {
          Text("Connections: ${connections.toInt()}", style = MaterialTheme.typography.titleSmall)
          Slider(
            value = connections,
            onValueChange = { connections = it },
            valueRange = 1f..DownloadPreferences.MAX_CONNECTIONS.toFloat(),
            steps = DownloadPreferences.MAX_CONNECTIONS - 2,
          )
        }
      }
      LabeledRow("Wi-Fi only") { Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it }) }
      LabeledRow("Play while downloading") { Switch(checked = playNow, onCheckedChange = { playNow = it }) }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { confirm(startNow = false) }, enabled = valid) { Text("Add to queue") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { confirm(startNow = true) }, enabled = valid) { Text("Download") }
      }
    }
  }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
    content()
  }
}

fun clipboardText(context: Context): String? =
  context.getSystemService(ClipboardManager::class.java)?.primaryClip
    ?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.takeIf { it.isNotBlank() }
