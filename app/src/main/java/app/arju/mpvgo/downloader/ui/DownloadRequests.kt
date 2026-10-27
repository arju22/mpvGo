package app.arju.mpvgo.downloader.ui

import android.content.Context
import android.widget.Toast
import app.arju.mpvgo.core.domain.media.model.Video
import app.arju.mpvgo.core.utils.media.MediaFormatUtils
import app.arju.mpvgo.downloader.DownloadManager
import app.arju.mpvgo.downloader.DownloadStreamServer
import app.arju.mpvgo.downloader.data.DownloadEntity
import app.arju.mpvgo.downloader.data.DownloadStatus
import app.arju.mpvgo.player.MediaRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** What the browser, Open Link or the Downloads tab want to download; the sheet fills in the rest. */
data class DownloadDraft(
  val url: String = "",
  val title: String? = null,
  val mimeType: String? = null,
  val userAgent: String? = null,
  val referer: String? = null,
  val cookies: String? = null,
  val pageUrl: String? = null,
  val isPrivate: Boolean = false,
  /** Show the sheet even when "Ask before every download" is off. */
  val forceSheet: Boolean = false,
) {
  companion object {
    fun from(entity: DownloadEntity) = DownloadDraft(
      url = entity.url,
      title = entity.title,
      mimeType = entity.mimeType,
      userAgent = entity.userAgent,
      referer = entity.referer,
      cookies = entity.cookies,
      pageUrl = entity.sourcePageUrl,
      isPrivate = entity.isPrivate,
      forceSheet = true,
    )
  }
}

/** One pending request at a time, shown by [DownloadRequestHost] above every screen. */
object DownloadRequests {
  private val _pending = MutableStateFlow<DownloadDraft?>(null)
  val pending: StateFlow<DownloadDraft?> = _pending.asStateFlow()

  fun request(draft: DownloadDraft) {
    _pending.value = draft
  }

  fun dismiss() {
    _pending.value = null
  }
}

object DownloadPlayback {
  private const val WAIT_MS = 20_000L
  private const val TAIL_BYTES = 2L * 1024 * 1024

  /**
   * Plays a download in mpv while it's still running: through the loopback server when the
   * server supports ranges, otherwise straight from the source with the page's headers.
   */
  suspend fun play(context: Context, manager: DownloadManager, server: DownloadStreamServer, id: Long) {
    var waited = 0L
    while (waited < WAIT_MS) {
      val entity = manager.get(id) ?: return
      when {
        entity.status == DownloadStatus.Completed -> return playCompleted(context, entity)
        entity.status == DownloadStatus.Failed -> {
          Toast.makeText(context, entity.failReason?.message ?: "Download failed", Toast.LENGTH_SHORT).show()
          return
        }
        entity.isHls -> return playSource(context, entity)
        manager.plan(id) != null -> {
          val plan = manager.plan(id)!!
          if (plan.total > TAIL_BYTES * 2) plan.prioritize(plan.total - TAIL_BYTES)
          context.startActivity(MediaRequest(server.urlFor(id, entity.fileName), title = displayTitle(entity)).toIntent(context))
          return
        }
        entity.totalBytes > 0 && !entity.supportsResume && entity.downloadedBytes > 0 -> return playSource(context, entity)
      }
      delay(250)
      waited += 250
    }
    manager.get(id)?.let { playSource(context, it) }
  }

  fun playSource(context: Context, entity: DownloadEntity) {
    context.startActivity(
      MediaRequest(
        url = entity.url,
        title = displayTitle(entity),
        userAgent = entity.userAgent,
        referer = entity.referer,
        cookies = entity.cookies,
        pageUrl = entity.sourcePageUrl,
        isPrivate = entity.isPrivate,
      ).toIntent(context),
    )
  }

  fun playCompleted(context: Context, entity: DownloadEntity) {
    val video = entity.toVideo() ?: return
    app.arju.mpvgo.core.utils.media.MediaUtils.playFile(video, context, "downloads")
  }

  fun displayTitle(entity: DownloadEntity): String = entity.fileName.substringBeforeLast('.')
}

/** Lets completed downloads reuse the library's VideoCard (thumbnail, duration, resolution chips). */
fun DownloadEntity.toVideo(): Video? {
  val path = finalPath ?: return null
  val file = File(path)
  val uri = finalUri?.let(android.net.Uri::parse) ?: android.net.Uri.fromFile(file)
  val size = if (file.exists()) file.length() else totalBytes.coerceAtLeast(0)
  return Video(
    id = id,
    title = fileName.substringBeforeLast('.'),
    displayName = fileName,
    path = path,
    uri = uri,
    duration = durationMs,
    durationFormatted = if (durationMs > 0) MediaFormatUtils.formatDuration(durationMs) else "",
    size = size,
    sizeFormatted = MediaFormatUtils.formatFileSize(size),
    dateModified = (completedAt ?: createdAt) / 1000,
    dateAdded = (completedAt ?: createdAt) / 1000,
    mimeType = mimeType ?: "video/*",
    bucketId = file.parent.orEmpty(),
    bucketDisplayName = file.parentFile?.name.orEmpty(),
    width = width,
    height = height,
    fps = 0f,
    resolution = MediaFormatUtils.formatResolution(width, height),
  )
}
