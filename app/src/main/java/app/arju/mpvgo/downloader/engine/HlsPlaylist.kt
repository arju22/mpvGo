package app.arju.mpvgo.downloader.engine

import java.net.URI

/** Minimal HLS (m3u8) parser: master variants, media segments, AES-128 keys and fMP4 init maps. */
object HlsPlaylist {

  data class Variant(val url: String, val bandwidth: Long, val width: Int, val height: Int, val codecs: String?) {
    val label: String get() = if (height > 0) "${height}p" else "${bandwidth / 1000} kbps"
  }

  data class Key(val method: String, val uri: String?, val iv: ByteArray?) {
    override fun equals(other: Any?) =
      other is Key && method == other.method && uri == other.uri && iv.contentEquals(other.iv)

    override fun hashCode() = 31 * (31 * method.hashCode() + (uri?.hashCode() ?: 0)) + (iv?.contentHashCode() ?: 0)
  }

  data class Segment(val url: String, val duration: Double, val sequence: Long, val key: Key?)

  data class Media(
    val segments: List<Segment>,
    val initUrl: String?,
    val isLive: Boolean,
    val totalDuration: Double,
  ) {
    /** Methods other than NONE/AES-128 (e.g. SAMPLE-AES) are DRM-like and can't be saved. */
    val hasUnsupportedEncryption: Boolean
      get() = segments.any { it.key != null && it.key.method != "AES-128" }
  }

  sealed interface Parsed

  data class Master(val variants: List<Variant>) : Parsed

  data class MediaPlaylist(val media: Media) : Parsed

  fun isPlaylist(text: String): Boolean = text.trimStart('\uFEFF', ' ', '\n', '\r').startsWith("#EXTM3U")

  fun parse(text: String, baseUrl: String): Parsed =
    if (text.contains("#EXT-X-STREAM-INF")) parseMaster(text, baseUrl) else MediaPlaylist(parseMedia(text, baseUrl))

  fun parseMaster(text: String, baseUrl: String): Master {
    val variants = mutableListOf<Variant>()
    var pending: Map<String, String>? = null
    for (raw in text.lineSequence()) {
      val line = raw.trim()
      when {
        line.startsWith("#EXT-X-STREAM-INF:") -> pending = attributes(line.substringAfter(':'))
        line.isEmpty() || line.startsWith("#") -> Unit
        pending != null -> {
          val a = pending
          val res = a["RESOLUTION"]?.split('x')
          variants += Variant(
            url = resolve(baseUrl, line),
            bandwidth = a["BANDWIDTH"]?.toLongOrNull() ?: 0,
            width = res?.getOrNull(0)?.toIntOrNull() ?: 0,
            height = res?.getOrNull(1)?.toIntOrNull() ?: 0,
            codecs = a["CODECS"],
          )
          pending = null
        }
      }
    }
    return Master(variants.sortedWith(compareByDescending<Variant> { it.height }.thenByDescending { it.bandwidth }))
  }

  fun parseMedia(text: String, baseUrl: String): Media {
    val segments = mutableListOf<Segment>()
    var sequence = 0L
    var duration = 0.0
    var key: Key? = null
    var initUrl: String? = null
    var ended = false
    var sawSegmentLine = false
    for (raw in text.lineSequence()) {
      val line = raw.trim()
      when {
        line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> if (!sawSegmentLine) {
          sequence = line.substringAfter(':').trim().toLongOrNull() ?: 0
        }
        line.startsWith("#EXTINF:") -> duration = line.substringAfter(':').substringBefore(',').trim().toDoubleOrNull() ?: 0.0
        line.startsWith("#EXT-X-KEY:") -> {
          val a = attributes(line.substringAfter(':'))
          val method = a["METHOD"] ?: "NONE"
          key = if (method == "NONE") null else Key(method, a["URI"]?.let { resolve(baseUrl, it) }, a["IV"]?.let(::hex))
        }
        line.startsWith("#EXT-X-MAP:") -> initUrl = attributes(line.substringAfter(':'))["URI"]?.let { resolve(baseUrl, it) }
        line.startsWith("#EXT-X-ENDLIST") -> ended = true
        line.isEmpty() || line.startsWith("#") -> Unit
        else -> {
          sawSegmentLine = true
          segments += Segment(resolve(baseUrl, line), duration, sequence++, key)
          duration = 0.0
        }
      }
    }
    return Media(segments, initUrl, isLive = !ended, totalDuration = segments.sumOf { it.duration })
  }

  /** Default IV per RFC 8216: the media sequence number as a 16-byte big-endian integer. */
  fun ivFor(segment: Segment): ByteArray =
    segment.key?.iv ?: ByteArray(16).also { b -> for (i in 0 until 8) b[15 - i] = (segment.sequence ushr (8 * i)).toByte() }

  /** Picks the variant matching [preferredHeight] (closest not above it), else the best. */
  fun choose(variants: List<Variant>, preferredHeight: Int?): Variant? {
    if (variants.isEmpty()) return null
    if (preferredHeight == null || preferredHeight <= 0) return variants.first()
    return variants.filter { it.height in 1..preferredHeight }.maxByOrNull { it.height * 1_000_000L + it.bandwidth }
      ?: variants.minByOrNull { if (it.height > 0) it.height else Int.MAX_VALUE }
  }

  internal fun attributes(s: String): Map<String, String> {
    val out = mutableMapOf<String, String>()
    var i = 0
    while (i < s.length) {
      val eq = s.indexOf('=', i).takeIf { it >= 0 } ?: break
      val name = s.substring(i, eq).trim().trimStart(',').trim()
      var j = eq + 1
      val value: String
      if (j < s.length && s[j] == '"') {
        val close = s.indexOf('"', j + 1).let { if (it < 0) s.length else it }
        value = s.substring(j + 1, close)
        j = close + 1
      } else {
        val comma = s.indexOf(',', j).let { if (it < 0) s.length else it }
        value = s.substring(j, comma).trim()
        j = comma
      }
      out[name] = value
      i = if (j < s.length && s[j] == ',') j + 1 else j
    }
    return out
  }

  private fun hex(s: String): ByteArray? {
    val h = s.removePrefix("0x").removePrefix("0X")
    if (h.length % 2 != 0) return null
    return runCatching { ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }.getOrNull()
      ?.let { if (it.size < 16) ByteArray(16 - it.size) + it else it }
  }

  fun resolve(base: String, ref: String): String = runCatching { URI(base).resolve(ref.trim()).toString() }.getOrDefault(ref)
}
