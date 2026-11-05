package app.arju.mpvgo.webbrowser.core

import java.util.Locale

enum class MediaKind { HLS, DASH, FILE }

/** Decides whether a request is a playable video/audio stream (not a segment, ad pixel, etc.). */
object MediaClassifier {
  private val FILE_EXT = setOf("mp4", "webm", "mkv", "m4v", "mov", "3gp", "flv", "avi", "mp3", "m4a", "ogg", "opus", "flac", "wav")
  private val SEGMENT_EXT = setOf("ts", "m4s", "aac", "vtt", "key", "cmfv", "cmfa", "m4f")
  private val SEGMENT_HINT = Regex("""(^|[/_\-.])(seg|segment|chunk|frag|fragment|init)(?=[\-_.]?\d|\.)""", RegexOption.IGNORE_CASE)
  private val RANGE_PATH = Regex("""/range/\d+-\d+""")
  private val AUDIO_ONLY = setOf("mp3", "m4a", "ogg", "opus", "flac", "wav")

  fun classify(
    url: String,
    mime: String? = null,
  ): MediaKind? {
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null
    val m = mime?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
    val lower = url.lowercase(Locale.ROOT)
    val path = lower.substringBefore('#').substringBefore('?')
    val ext = path.substringAfter("://").substringAfter('/', "").substringAfterLast('/').substringAfterLast('.', "")
    when (m) {
      "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> return MediaKind.HLS
      "application/dash+xml" -> return MediaKind.DASH
    }
    when (ext) {
      "m3u8" -> return MediaKind.HLS
      "mpd" -> return MediaKind.DASH
    }
    if (ext in SEGMENT_EXT || m == "video/mp2t" || m == "video/iso.segment") return null
    if (isSegmentLike(path)) return null
    if (ext in FILE_EXT) return MediaKind.FILE
    if (m.startsWith("video/") || m.startsWith("audio/")) return MediaKind.FILE
    val query = lower.substringBefore('#').substringAfter('?', "")
    return when {
      query.contains("type=m3u8") || query.contains("format=m3u8") -> MediaKind.HLS
      query.contains("mime=video") || query.contains("mime_type=video") -> MediaKind.FILE
      else -> null
    }
  }

  fun isAudioOnly(
    url: String,
    mime: String? = null,
  ): Boolean {
    val m = mime?.lowercase(Locale.ROOT).orEmpty()
    if (m.startsWith("audio/") && !m.contains("mpegurl")) return true
    val ext = url.lowercase(Locale.ROOT).substringBefore('?').substringAfterLast('/').substringAfterLast('.', "")
    return ext in AUDIO_ONLY
  }

  fun isSegment(
    url: String,
    accept: String? = null,
    range: String? = null,
  ): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    val path = lower.substringBefore('#').substringBefore('?')
    val ext = path.substringAfterLast('.', "")
    if (ext in SEGMENT_EXT || isSegmentLike(path)) return true
    if (accept != null && (accept.contains("video/mp2t") || accept.contains("video/iso.segment"))) return true
    if (range != null && range.startsWith("bytes=")) return true
    return false
  }

  private fun isSegmentLike(path: String): Boolean {
    val last = path.substringAfterLast('/')
    return SEGMENT_HINT.containsMatchIn(last) || RANGE_PATH.containsMatchIn(path)
  }

  /** Key used to drop duplicates: scheme-less URL without fragment and volatile range params. */
  fun dedupKey(url: String): String {
    val noFrag = url.substringBefore('#').substringAfter("://")
    val base = noFrag.substringBefore('?')
    val query =
      noFrag
        .substringAfter('?', "")
        .split('&')
        .filter { it.isNotEmpty() && !it.startsWith("range=") && !it.startsWith("rn=") && !it.startsWith("rbuf=") }
        .sorted()
        .joinToString("&")
    return if (query.isEmpty()) base else "$base?$query"
  }

  /** YouTube stays in the web player (policy). */
  fun isBlockedForPlayback(pageUrl: String): Boolean {
    val host = pageUrl.substringAfter("://").substringBefore('/').substringBefore(':').lowercase(Locale.ROOT)
    return host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" ||
      host.endsWith("youtube-nocookie.com") || host.endsWith("googlevideo.com")
  }
}
