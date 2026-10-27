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
  val mime: String?,
  /** The frame that requested it; sent as Referer. */
  val frameUrl: String,
  val pageUrl: String,
  val pageTitle: String,
  val size: Long,
  val audioOnly: Boolean,
  val variants: List<HlsPlaylist.Variant> = emptyList(),
  val seq: Long,
) {
  val key: String get() = MediaClassifier.dedupKey(url)

  /** Best height label, e.g. "1080p", when known. */
  val quality: String? get() = variants.maxOfOrNull { it.height }?.takeIf { it > 0 }?.let { "${it}p" }
}

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

  private val variantKeys = HashSet<String>()
  private var seq = 0L
  private var generation = 0

  @Synchronized
  fun reset() {
    generation++
    variantKeys.clear()
    _media.value = emptyList()
    _drm.value = false
  }

  fun markDrm() {
    _drm.value = true
  }

  fun onRequest(
    url: String,
    mime: String?,
    frameUrl: String,
    pageUrl: String,
    pageTitle: String,
    size: Long = -1,
  ) {
    val kind = MediaClassifier.classify(url, mime) ?: return
    val key = MediaClassifier.dedupKey(url)
    val gen: Int
    synchronized(this) {
      if (key in variantKeys) return
      val current = _media.value
      val existing = current.indexOfFirst { it.key == key }
      if (existing >= 0) {
        val old = current[existing]
        if ((old.mime == null && mime != null) || (old.size < 0 && size >= 0) || old.pageTitle != pageTitle) {
          _media.value =
            current.toMutableList().also {
              it[existing] = old.copy(mime = old.mime ?: mime, size = if (old.size < 0) size else old.size, pageTitle = pageTitle)
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
        )
      gen = generation
    }
    if (kind == MediaKind.HLS) scope.launch { inspectHls(url, frameUrl, gen) }
  }

  /** Folds variant playlists into their master and records the qualities. */
  private suspend fun inspectHls(
    url: String,
    referer: String,
    gen: Int,
  ) {
    val text = runCatching { fetchText(url, referer) }.getOrNull() ?: return
    if (!HlsPlaylist.isPlaylist(text)) return
    val parsed = runCatching { HlsPlaylist.parse(text, url) }.getOrNull() as? HlsPlaylist.Master ?: return
    synchronized(this) {
      if (gen != generation) return
      val keys = parsed.variants.map { MediaClassifier.dedupKey(it.url) }.toSet()
      variantKeys += keys
      _media.update { list ->
        list
          .filterNot { it.key in keys }
          .map { if (it.key == MediaClassifier.dedupKey(url)) it.copy(variants = parsed.variants) else it }
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
