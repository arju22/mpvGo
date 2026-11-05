package app.arju.mpvgo.webbrowser.ui

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.arju.mpvgo.webbrowser.core.BrowserDownloads
import app.arju.mpvgo.webbrowser.core.DetectedMedia
import app.arju.mpvgo.webbrowser.core.MediaEvent
import app.arju.mpvgo.webbrowser.core.MediaKind
import java.util.Locale

/** The page started a video: play it in mpvGo, download it, keep the web player or copy the link. */
@Composable
fun VideoAskDialog(
  host: String,
  title: String,
  media: DetectedMedia?,
  showRemember: Boolean,
  onPlay: (remember: Boolean) -> Unit,
  onDownload: () -> Unit,
  onWebPlayer: (remember: Boolean) -> Unit,
  onCopyLink: () -> Unit,
  onDismiss: () -> Unit,
) {
  var always by remember(host) { mutableStateOf(true) }
  AlertDialog(
    onDismissRequest = onDismiss,
    confirmButton = {},
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(host, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (title.isNotBlank()) {
          Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(
          media?.let(BrowserDownloads::streamInfo) ?: "No stream found yet. You can still play it on the page.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = { onPlay(always) }, enabled = media != null, modifier = Modifier.fillMaxWidth()) {
          ButtonContent(Icons.Filled.PlayArrow, "Play in mpvGo")
        }
        OutlinedButton(onClick = onDownload, enabled = media != null, modifier = Modifier.fillMaxWidth()) {
          ButtonContent(Icons.Filled.Download, "Download")
        }
        OutlinedButton(onClick = { onWebPlayer(always) }, modifier = Modifier.fillMaxWidth()) {
          ButtonContent(Icons.Filled.Language, "Use web player")
        }
        TextButton(onClick = onCopyLink, enabled = media != null, modifier = Modifier.fillMaxWidth()) {
          ButtonContent(Icons.Filled.ContentCopy, "Copy link")
        }
        if (showRemember) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { always = !always }) {
            Checkbox(checked = always, onCheckedChange = { always = it })
            Text("Always do this on $host", style = MaterialTheme.typography.bodyMedium)
          }
        }
      }
    },
  )
}

@Composable
private fun ButtonContent(
  icon: ImageVector,
  label: String,
) {
  Icon(icon, null)
  Spacer(Modifier.width(8.dp))
  Text(label)
}

/** "N videos found" sheet behind the floating media button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoundVideosSheet(
  host: String,
  media: List<DetectedMedia>,
  hasHiddenPlaylist: Boolean = false,
  blobMseDetected: Boolean = false,
  isDownloaded: (DetectedMedia) -> Boolean,
  onPlay: (DetectedMedia) -> Unit,
  onDownload: (DetectedMedia) -> Unit,
  onDownloadAll: (List<DetectedMedia>) -> Unit,
  onClear: () -> Unit,
  onDismiss: () -> Unit,
  onShowDetectionLog: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val sorted = media.sortedByDescending { it.seq }
  val pending = sorted.filterNot(isDownloaded)
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Column(Modifier.padding(horizontal = 16.dp)) {
      Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(Modifier.weight(1f)) {
          Text(
            if (sorted.isEmpty()) "Videos" else if (sorted.size == 1) "1 video found" else "${sorted.size} videos found",
            style = MaterialTheme.typography.titleMedium,
          )
          if (host.isNotBlank()) {
            Text(host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
        if (onShowDetectionLog != null) {
          TextButton(onClick = onShowDetectionLog) {
            Text("Log", style = MaterialTheme.typography.labelMedium)
          }
        }
      }
    }
    if (sorted.isEmpty()) {
      if (hasHiddenPlaylist) {
        Card(
          modifier = Modifier.padding(16.dp).fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
              Text("Stream found but its playlist is hidden", style = MaterialTheme.typography.titleSmall)
              Text(
                "This site loads video segments directly without exposing its playlist.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else if (blobMseDetected) {
        Card(
          modifier = Modifier.padding(16.dp).fillMaxWidth(),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
              Text("This site streams in a way mpvGo can't capture", style = MaterialTheme.typography.titleSmall)
              Text(
                "This video uses MediaSource blob streaming without an accessible URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        Text(
          "No videos found on this page",
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(24.dp),
        )
      }
    } else {
      LazyColumn(Modifier.heightIn(max = 420.dp)) {
        items(sorted, key = { it.key }) { item ->
          val done = isDownloaded(item)
          ListItem(
            headlineContent = {
              Text(BrowserDownloads.fileName(item.url).ifBlank { item.url }, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
              val size = if (item.size > 0 && item.kind != MediaKind.HLS) Formatter.formatShortFileSize(context, item.size) else null
              Text(listOfNotNull(BrowserDownloads.streamInfo(item), size).joinToString(" · "))
            },
            trailingContent = {
              Row(verticalAlignment = Alignment.CenterVertically) {
                if (done) {
                  Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                  Spacer(Modifier.width(4.dp))
                  Text("Downloaded", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                  IconButton(onClick = { onDownload(item) }) { Icon(Icons.Filled.Download, "Download") }
                }
                IconButton(onClick = { onPlay(item) }) { Icon(Icons.Filled.PlayArrow, "Play in mpvGo") }
              }
            },
          )
        }
      }
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onClear) { Text("Clear list") }
        Button(onClick = { onDownloadAll(pending) }, enabled = pending.isNotEmpty()) {
          Text(if (pending.size > 1) "Download all (${pending.size})" else "Download all")
        }
      }
    }
    Spacer(Modifier.height(16.dp))
  }
}

@Composable
fun DetectionLogDialog(
  events: List<MediaEvent>,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Detection log (last ${events.size})") },
    text = {
      if (events.isEmpty()) {
        Text("No media events recorded for this tab.")
      } else {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
          items(events.reversed()) { ev ->
            Column(Modifier.padding(vertical = 4.dp)) {
              Text(sdf.format(java.util.Date(ev.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
              Text(ev.message, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val text = events.joinToString("\n") { "${sdf.format(java.util.Date(it.timestamp))}: ${it.message}" }
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Detection log", text))
        Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
      }) {
        Text("Copy")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}

@Composable
fun AlreadyDownloadedDialog(
  fileName: String,
  onPlay: () -> Unit,
  onDownloadAgain: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Already downloaded") },
    text = { Text("\"$fileName\" is already in your downloads.") },
    confirmButton = { TextButton(onClick = onPlay) { Text("Play") } },
    dismissButton = {
      Row {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        TextButton(onClick = onDownloadAgain) { Text("Download again") }
      }
    },
  )
}

@Composable
fun CantDownloadDialog(
  message: String,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Can't download this video") },
    text = { Text(message) },
    confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
  )
}

enum class LinkAction { NEW_TAB, PRIVATE_TAB, PLAY, DOWNLOAD, COPY, SHARE }

/** Long-press menu for a link on the page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkMenuSheet(
  url: String,
  isPrivateTab: Boolean,
  isVideo: Boolean,
  onAction: (LinkAction) -> Unit,
  onDismiss: () -> Unit,
) {
  ModalBottomSheet(onDismissRequest = onDismiss) {
    Text(
      url,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.padding(horizontal = 16.dp),
    )
    Spacer(Modifier.height(8.dp))
    @Composable
    fun item(
      label: String,
      icon: ImageVector,
      action: LinkAction,
    ) = ListItem(
      headlineContent = { Text(label) },
      leadingContent = { Icon(icon, null) },
      modifier = Modifier.clickable { onAction(action) },
    )
    if (isPrivateTab) {
      item("Open in new private tab", Icons.Filled.VisibilityOff, LinkAction.PRIVATE_TAB)
    } else {
      item("Open in new tab", Icons.AutoMirrored.Filled.OpenInNew, LinkAction.NEW_TAB)
      item("Open in private tab", Icons.Filled.VisibilityOff, LinkAction.PRIVATE_TAB)
    }
    if (isVideo) item("Play in mpvGo", Icons.Filled.PlayArrow, LinkAction.PLAY)
    item("Download link", Icons.Filled.Download, LinkAction.DOWNLOAD)
    item("Copy link", Icons.Filled.ContentCopy, LinkAction.COPY)
    item("Share link", Icons.Filled.Share, LinkAction.SHARE)
    Spacer(Modifier.height(16.dp))
  }
}
