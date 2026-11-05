package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.downloader.engine.HlsPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DetectedMedia(
  val url: String,
  val kind: MediaKind,
  val mime: String? = null,
  /** The frame that requested it; sent as Referer. */
  val frameUrl: String = "",
  val pageUrl: String = "",
  val pageTitle: String = "",
  val size: Long = 0L,
  val audioOnly: Boolean = false,
  val variants: List<HlsPlaylist.Variant> = emptyList(),
  val seq: Long = 0L,
  val title: String = "",
  val height: Int = 0,
  val duration: Double = 0.0,
  val estimatedSize: Long = 0L,
) {
  val key: String get() = MediaClassifier.dedupKey(url)

  /** Best height label, e.g. "1080p", when known. */
  val quality: String?
    get() = variants.maxOfOrNull { it.height }?.takeIf { it > 0 }?.let { "${it}p" }
      ?: (if (height > 0) "${height}p" else null)
}

data class MediaEvent(
  val timestamp: Long = System.currentTimeMillis(),
  val message: String,
)

/**
 * Collects the streams one tab's page loads, from network requests and the JS hook.
 * Thread-safe: WebView network threads and the JS bridge both report here.
 */
class MediaSniffer(
  private val scope: CoroutineScope,
  /** Fetches a playlist with the page's headers; returns the text or null. */
  private val fetchText: suspend (url: String, referer: String) -> String?,
) {
  private val _media = MutableStateFlow<List<DetectedMedia>>(emptyList())

  /** Streams found on the current page, newest last; HLS variants of a master are folded in. */
  val media: StateFlow<List<DetectedMedia>> = _media.asStateFlow()

  private val _drm = MutableStateFlow(false)
  val drm: StateFlow<Boolean> = _drm.asStateFlow()

  private val _hasHiddenPlaylist = MutableStateFlow(false)
  val hasHiddenPlaylist: StateFlow<Boolean> = _hasHiddenPlaylist.asStateFlow()

  private val _blobMseDetected = MutableStateFlow(false)
  val blobMseDetected: StateFlow<Boolean> = _blobMseDetected.asStateFlow()

  private val _detectionLog = MutableStateFlow<List<MediaEvent>>(emptyList())
  val detectionLog: StateFlow<List<MediaEvent>> = _detectionLog.asStateFlow()

  private val variantKeys = HashSet<String>()
  private var seq = 0L
  private var generation = 0

  fun logEvent(message: String) {
    synchronized(this) {
      val current = _detectionLog.value
      val next = current + MediaEvent(message = message)
      _detectionLog.value = if (next.size > 50) next.takeLast(50) else next
    }
  }

  @Synchronized
  fun reset() {
    generation++
    variantKeys.clear()
    _media.value = emptyList()
    _drm.value = false
    _hasHiddenPlaylist.value = false
    _blobMseDetected.value = false
    _detectionLog.value = emptyList()
  }

  fun markDrm() {
    _drm.value = true
    logEvent("drm_detected")
  }

  fun onSegmentSeen(url: String) {
    if (_media.value.isEmpty()) {
      _hasHiddenPlaylist.value = true
    }
    logEvent("segment: $url")
  }

  fun onBlobMse(mime: String? = null) {
    _blobMseDetected.value = true
    logEvent("blob_mse${if (mime != null) " mime=$mime" else ""}")
  }

  /** "Clear list": forgets what was found so far; new streams still show up. */
  @Synchronized
  fun clear() {
    _media.value = emptyList()
  }

  fun onRequest(
    url: String,
    mime: String?,
    frameUrl: String,
    pageUrl: String,
    pageTitle: String,
    size: Long = -1,
    title: String = "",
    height: Int = 0,
  ) {
    val kind = MediaClassifier.classify(url, mime)
    if (kind == null) {
      logEvent("ignored: $url mime=$mime")
      return
    }
    logEvent("found: $kind $url mime=$mime size=$size")
    val key = MediaClassifier.dedupKey(url)
    val gen: Int
    synchronized(this) {
      if (key in variantKeys) return
      val current = _media.value
      val existing = current.indexOfFirst { it.key == key }
      if (existing >= 0) {
        val old = current[existing]
        if ((old.mime == null && mime != null) || (old.size < 0 && size >= 0) || old.pageTitle != pageTitle || (old.title.isBlank() && title.isNotBlank())) {
          _media.value =
            current.toMutableList().also {
              it[existing] = old.copy(
                mime = old.mime ?: mime,
                size = if (old.size < 0) size else old.size,
                pageTitle = pageTitle,
                title = if (old.title.isBlank()) title else old.title,
                height = if (old.height <= 0) height else old.height,
              )
            }
        }
        return
      }
      _media.value =
        current +
        DetectedMedia(
          url = url,
          kind = kind,
          mime = mime?.takeIf { it.isNotBlank() },
          frameUrl = frameUrl,
          pageUrl = pageUrl,
          pageTitle = pageTitle,
          size = size,
          audioOnly = MediaClassifier.isAudioOnly(url, mime),
          seq = ++seq,
          title = title,
          height = height,
        )
      gen = generation
    }
    if (kind == MediaKind.HLS) {
      scope.launch { inspectHls(url, frameUrl, gen) }
    } else if (kind == MediaKind.FILE && size <= 0L) {
      scope.launch { inspectFile(url, frameUrl, gen) }
    }
  }

  /** Folds variant playlists into their master and records the qualities and estimated sizes. */
  private suspend fun inspectHls(
    url: String,
    referer: String,
    gen: Int,
  ) {
    val text = runCatching { fetchText(url, referer) }.getOrNull() ?: return
    if (!HlsPlaylist.isPlaylist(text)) return
    val parsed = runCatching { HlsPlaylist.parse(text, url) }.getOrNull() ?: return
    synchronized(this) {
      if (gen != generation) return
      when (parsed) {
        is HlsPlaylist.Master -> {
          val keys = parsed.variants.map { MediaClassifier.dedupKey(it.url) }.toSet()
          variantKeys += keys
          val bestVariant = parsed.variants.maxByOrNull { it.bandwidth }
          _media.update { list ->
            list
              .filterNot { it.key in keys }
              .map { if (it.key == MediaClassifier.dedupKey(url)) it.copy(variants = parsed.variants) else it }
          }
          if (bestVariant != null) {
            scope.launch {
              val vText = runCatching { fetchText(bestVariant.url, referer) }.getOrNull()
              if (vText != null && HlsPlaylist.isPlaylist(vText)) {
                val vParsed = runCatching { HlsPlaylist.parse(vText, bestVariant.url) }.getOrNull() as? HlsPlaylist.MediaPlaylist
                if (vParsed != null && vParsed.media.totalDuration > 0 && bestVariant.bandwidth > 0) {
                  val est = ((bestVariant.bandwidth / 8.0) * vParsed.media.totalDuration).toLong()
                  synchronized(this@MediaSniffer) {
                    if (gen != generation) return@synchronized
                    _media.update { l ->
                      l.map { if (it.key == MediaClassifier.dedupKey(url)) it.copy(duration = vParsed.media.totalDuration, estimatedSize = est) else it }
                    }
                  }
                }
              }
            }
          }
        }
        is HlsPlaylist.MediaPlaylist -> {
          val dur = parsed.media.totalDuration
          if (dur > 0) {
            _media.update { list ->
              list.map { if (it.key == MediaClassifier.dedupKey(url)) it.copy(duration = dur) else it }
            }
          }
        }
      }
    }
  }

  private suspend fun inspectFile(
    url: String,
    referer: String,
    gen: Int,
  ) {
    val len = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      runCatching {
        val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "HEAD"
        if (referer.isNotBlank()) conn.setRequestProperty("Referer", referer)
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        val cl = if (code in 200..299) conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L else -1L
        conn.disconnect()
        cl
      }.getOrDefault(-1L)
    }
    if (len > 0L) {
      synchronized(this) {
        if (gen != generation) return
        _media.update { list ->
          list.map { if (it.key == MediaClassifier.dedupKey(url)) it.copy(size = len) else it }
        }
      }
    }
  }

  /** The stream that matches a media element's src, else the best guess for what's playing. */
  fun match(src: String?): DetectedMedia? {
    val list = _media.value
    if (list.isEmpty()) return null
    if (!src.isNullOrBlank() && !src.startsWith("blob:")) {
      val key = MediaClassifier.dedupKey(src)
      list.firstOrNull { it.key == key }?.let { return it }
    }
    return best(list)
  }

  companion object {
    /** Prefer adaptive manifests (HLS master > HLS > DASH), then video files; newest wins ties. */
    fun best(list: List<DetectedMedia>): DetectedMedia? =
      list.maxWithOrNull(
        compareBy<DetectedMedia> { !it.audioOnly }
          .thenBy {
            when (it.kind) {
              MediaKind.HLS -> if (it.variants.isNotEmpty()) 3 else 2
              MediaKind.DASH -> 1
              MediaKind.FILE -> 0
            }
          }.thenBy { it.seq },
      )
  }
}
