package app.arju.mpvgo.downloader.engine

import java.net.URLDecoder

/** Builds readable file names from a page title, a link, Content-Disposition and MIME type. */
object DownloadNaming {

  private val videoExtensions = setOf(
    "mp4", "mkv", "webm", "mov", "avi", "m4v", "ts", "flv", "wmv", "3gp", "mpg", "mpeg", "ogv",
    "mp3", "m4a", "aac", "flac", "opus", "ogg", "wav",
  )

  private val genericStems = setOf(
    "master", "index", "playlist", "video", "playback", "manifest", "chunklist", "stream", "media",
    "file", "download", "source", "output", "prog_index", "main", "mono", "hls", "dash", "default",
  )

  private val siteSuffix = Regex("""\s+[-|–—:·]\s+[^-|–—]{2,40}$""")
  private val watchPrefix = Regex("""^(watch|download|stream)\s+""", RegexOption.IGNORE_CASE)
  private val noiseSuffix = Regex(
    """(\s+(online|free|full|hd|fhd|uhd|for free|in hd|english sub(bed)?|eng sub|sub|dub(bed)?))+\s*$""",
    RegexOption.IGNORE_CASE,
  )
  private val illegal = Regex("""[\\/:*?"<>|\u0000-\u001f]""")

  /**
   * The best file name for a download, without guessing the folder. A clean page title wins over generic
   * link names such as master.m3u8; otherwise the link's own name is kept.
   */
  fun suggest(
    url: String,
    pageTitle: String? = null,
    contentDisposition: String? = null,
    mimeType: String? = null,
    isHls: Boolean = false,
  ): String {
    val fromHeader = contentDisposition?.let(::fileNameFromDisposition)
    val fromUrl = fileNameFromUrl(url)
    val urlStem = fromUrl?.substringBeforeLast('.')
    val ext = when {
      isHls -> "mp4"
      fromHeader?.let(::extensionOf) != null -> extensionOf(fromHeader)!!
      fromUrl?.let(::extensionOf) != null -> extensionOf(fromUrl)!!
      else -> extensionForMime(mimeType) ?: "mp4"
    }
    val title = pageTitle?.let(::cleanTitle)?.takeIf { it.length >= 3 }
    val stem = when {
      fromHeader != null && !isHls -> fromHeader.substringBeforeLast('.')
      title != null && (isHls || urlStem == null || isGeneric(urlStem)) -> title
      urlStem != null && !isGeneric(urlStem) -> urlStem
      title != null -> title
      else -> urlStem ?: "video"
    }
    return sanitize(stem).ifBlank { "video" } + "." + ext
  }

  fun cleanTitle(raw: String): String {
    var t = raw.replace(Regex("""\s+"""), " ").trim()
    repeat(2) {
      val m = siteSuffix.find(t)
      if (m != null && m.range.first >= 3) t = t.substring(0, m.range.first).trim()
    }
    t = watchPrefix.replace(t, "")
    t = noiseSuffix.replace(t, "").trim()
    return sanitize(t)
  }

  fun sanitize(name: String): String =
    illegal.replace(name, " ").replace(Regex("""\s+"""), " ").trim().trim('.', ' ').take(120).trim()

  fun isGeneric(stem: String): Boolean {
    val s = stem.lowercase().replace(Regex("""[-_.]?(\d+p|v\d+|a\d+|\d+)$"""), "").replace(Regex("""[-_.]?(\d+p|v\d+|a\d+|\d+)$"""), "")
    return s in genericStems || s.length <= 2 || s.matches(Regex("""[0-9a-f\-]{16,}"""))
  }

  fun fileNameFromUrl(url: String): String? {
    val noQuery = url.substringBefore('#').substringBefore('?')
    val schemeEnd = noQuery.indexOf("://")
    val path = (if (schemeEnd >= 0) noQuery.substring(schemeEnd + 3).substringAfter('/', "") else noQuery).trimEnd('/')
    val last = path.substringAfterLast('/').takeIf { it.isNotBlank() && !it.contains("://") } ?: return null
    return runCatching { URLDecoder.decode(last, "UTF-8") }.getOrDefault(last)
  }

  fun fileNameFromDisposition(header: String): String? {
    Regex("""filename\*\s*=\s*[^']*'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)?.let {
      return runCatching { URLDecoder.decode(it.groupValues[1].trim(), "UTF-8") }.getOrNull()?.let(::sanitize)
    }
    return Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)
      ?.groupValues?.get(1)?.trim()?.let(::sanitize)?.takeIf { it.isNotBlank() }
  }

  fun extensionOf(name: String): String? =
    name.substringAfterLast('.', "").lowercase().takeIf { it in videoExtensions }

  fun extensionForMime(mime: String?): String? = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
    "video/mp4" -> "mp4"
    "video/x-matroska", "video/matroska" -> "mkv"
    "video/webm" -> "webm"
    "video/quicktime" -> "mov"
    "video/mp2t" -> "ts"
    "video/x-msvideo" -> "avi"
    "video/x-flv" -> "flv"
    "video/3gpp" -> "3gp"
    "audio/mpeg" -> "mp3"
    "audio/mp4", "audio/x-m4a" -> "m4a"
    "audio/aac" -> "aac"
    "audio/flac" -> "flac"
    "audio/ogg" -> "ogg"
    "audio/webm" -> "webm"
    else -> null
  }

  fun isHlsUrl(url: String, mime: String? = null): Boolean =
    url.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true) ||
      mime?.lowercase()?.let { "mpegurl" in it } == true

  /** "name.mp4" → "name (1).mp4", "name (2).mp4", ... until [exists] is false. */
  fun unique(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name
    val stem = name.substringBeforeLast('.')
    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
    var i = 1
    while (true) {
      val candidate = "$stem ($i)$ext"
      if (!exists(candidate)) return candidate
      i++
    }
  }

  /** Replaces the extension, e.g. after an HLS download falls back from MP4 to TS. */
  fun withExtension(name: String, ext: String): String = name.substringBeforeLast('.') + "." + ext
}
