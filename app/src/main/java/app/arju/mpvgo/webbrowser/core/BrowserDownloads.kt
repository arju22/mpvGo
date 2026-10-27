package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.downloader.LiveProgress
import app.arju.mpvgo.downloader.data.DownloadEntity
import app.arju.mpvgo.downloader.data.DownloadStatus
import app.arju.mpvgo.downloader.ui.DownloadDraft
import app.arju.mpvgo.player.MediaRequest

/** Rules for downloading from the browser: what's allowed, what's already there, what to show. */
object BrowserDownloads {
  enum class Blocked(val message: String) {
    DRM("This video is protected (DRM). It can only be played on the page."),
    SITE("Downloading from this site isn't supported. You can still watch it on the page."),
    LOCAL("This file was created by the page itself, so it can't be downloaded."),
  }

  fun blocked(
    pageUrl: String,
    url: String,
    drm: Boolean,
  ): Blocked? =
    when {
      drm -> Blocked.DRM
      MediaClassifier.isBlockedForPlayback(pageUrl) || MediaClassifier.isBlockedForPlayback(url) -> Blocked.SITE
      !url.startsWith("http://", true) && !url.startsWith("https://", true) -> Blocked.LOCAL
      else -> null
    }

  /** Private tabs keep no source page, so nothing points back to what was browsed. */
  fun draft(
    request: MediaRequest,
    mimeType: String? = null,
  ): DownloadDraft =
    DownloadDraft(
      url = request.url,
      title = request.title,
      mimeType = mimeType,
      userAgent = request.userAgent,
      referer = request.referer,
      cookies = request.cookies,
      pageUrl = request.pageUrl.takeUnless { request.isPrivate },
      isPrivate = request.isPrivate,
    )

  fun findCompleted(
    downloads: List<DownloadEntity>,
    url: String,
  ): DownloadEntity? {
    val key = MediaClassifier.dedupKey(url)
    return downloads.firstOrNull { it.status == DownloadStatus.Completed && MediaClassifier.dedupKey(it.url) == key }
  }

  /** "HLS · up to 1080p", "MP4 · audio" ... */
  fun streamInfo(media: DetectedMedia): String {
    val type =
      when (media.kind) {
        MediaKind.HLS -> "HLS"
        MediaKind.DASH -> "DASH"
        MediaKind.FILE -> fileName(media.url).substringAfterLast('.', "").uppercase().takeIf { it.length in 2..4 } ?: "Video"
      }
    return listOfNotNull(type, media.quality?.let { "up to $it" }, if (media.audioOnly) "audio" else null).joinToString(" · ")
  }

  fun fileName(url: String): String = url.substringBefore('#').substringBefore('?').trimEnd('/').substringAfterLast('/')

  /**
   * The stream on the reopened source page that most likely replaces [oldUrl]: same kind,
   * preferring the same file name, otherwise the newest one.
   */
  fun pickRefresh(
    found: List<DetectedMedia>,
    oldUrl: String,
    isHls: Boolean,
  ): DetectedMedia? {
    val candidates = found.filter { !it.audioOnly && (it.kind == MediaKind.HLS) == isHls }
    val name = fileName(oldUrl)
    return candidates.filter { name.isNotEmpty() && fileName(it.url) == name }.maxByOrNull { it.seq }
      ?: candidates.maxByOrNull { it.seq }
  }

  /** Overall progress of running downloads: null when none, -1 when unknown, else 0..1. */
  fun progress(live: Collection<LiveProgress>): Float? {
    if (live.isEmpty()) return null
    val fractions =
      live.mapNotNull {
        when {
          it.total > 0 -> it.downloaded.toFloat() / it.total
          it.segmentsTotal > 0 -> it.segmentsDone.toFloat() / it.segmentsTotal
          else -> null
        }
      }
    if (fractions.isEmpty()) return -1f
    return (fractions.sum() / fractions.size).coerceIn(0f, 1f)
  }
}

/** A download whose link expired, waiting for the user to replay the video on its source page. */
object LinkRefresh {
  const val EXTRA_ID = "app.arju.mpvgo.extra.REFRESH_LINK_ID"
  const val EXTRA_PAGE = "app.arju.mpvgo.extra.REFRESH_LINK_PAGE"

  data class Request(
    val downloadId: Long,
    val pageUrl: String,
  )

  private val _pending = kotlinx.coroutines.flow.MutableStateFlow<Request?>(null)
  val pending: kotlinx.coroutines.flow.StateFlow<Request?> = _pending

  fun canRefresh(entity: DownloadEntity): Boolean = !entity.isPrivate && !entity.sourcePageUrl.isNullOrBlank()

  /** Returns the page to open in the browser, or null when [entity] can't be refreshed. */
  fun start(entity: DownloadEntity): String? {
    if (!canRefresh(entity)) return null
    _pending.value = Request(entity.id, entity.sourcePageUrl!!)
    return entity.sourcePageUrl
  }

  /** From the failed-download notification's "Refresh link" action. */
  fun startFrom(intent: android.content.Intent?): String? {
    val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: return null
    val page = intent.getStringExtra(EXTRA_PAGE)
    intent.removeExtra(EXTRA_ID)
    if (id < 0 || page.isNullOrBlank()) return null
    _pending.value = Request(id, page)
    return page
  }

  fun finish(request: Request) {
    _pending.compareAndSet(request, null)
  }
}
