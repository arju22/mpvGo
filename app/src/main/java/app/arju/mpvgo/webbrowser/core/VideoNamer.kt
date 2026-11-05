package app.arju.mpvgo.webbrowser.core

import app.arju.mpvgo.webbrowser.adblock.SiteMatcher

/**
 * Generates human-friendly video titles for found videos, downloads, and player playback.
 * Cleans site branding suffixes, extracts meaningful file names, and disambiguates duplicates.
 */
object VideoNamer {
  private val GENERIC_NAMES = setOf(
    "master", "index", "playlist", "chunklist", "video", "manifest", "stream",
    "output", "segment", "media", "play", "source", "file"
  )

  private val SITE_SUFFIX_PATTERNS = listOf(
    Regex("""\s*[-–—|•·/]\s*(?:YouTube|Vimeo|Dailymotion|Twitch|Facebook|Twitter|X|Reddit|TikTok|Instagram).*$""", RegexOption.IGNORE_CASE),
    Regex("""\s*[-–—|•·/]\s*(?:Watch\s+.*|Online\s+Free|Full\s+HD|HD|Official\s+Site|Stream\s+Online).*$""", RegexOption.IGNORE_CASE),
    Regex("""\s*[-–—|•·/]\s*[\w\s.-]+$""", RegexOption.IGNORE_CASE),
  )

  fun cleanTitle(title: String): String {
    var cleaned = title.trim()
    for (pat in SITE_SUFFIX_PATTERNS) {
      val match = pat.find(cleaned)
      if (match != null) {
        val stripped = cleaned.substring(0, match.range.first).trim()
        if (stripped.length >= 3) {
          cleaned = stripped
          break
        }
      }
    }
    return sanitize(cleaned)
  }

  fun isMeaningfulFileName(name: String): Boolean {
    val base = name.substringBeforeLast('.').trim().lowercase()
    if (base.length <= 2) return false
    if (base in GENERIC_NAMES) return false
    // Hex hash like e.g. "a1b2c3d4e5f6..."
    if (base.matches(Regex("""^[0-9a-f]{16,}$"""))) return false
    if (base.matches(Regex("""^video[_-]?\d*$"""))) return false
    return true
  }

  fun sanitize(name: String): String =
    name.replace(Regex("""[\\/:*?"<>|\r\n\t]"""), " ")
      .trim()
      .take(120)

  fun nameFor(
    media: DetectedMedia,
    index: Int = 0,
    totalWithSameName: Int = 1,
  ): String {
    val cleanedPageTitle = cleanTitle(media.pageTitle)
    val candidate = when {
      cleanedPageTitle.isNotBlank() && !isGenericPageTitle(cleanedPageTitle) -> cleanedPageTitle
      media.title.isNotBlank() -> sanitize(media.title)
      else -> {
        val fn = BrowserDownloads.fileName(media.url)
        if (isMeaningfulFileName(fn)) {
          fn.substringBeforeLast('.')
        } else {
          val host = SiteMatcher.host(media.pageUrl.ifBlank { media.url })
          if (host.isNotBlank()) host else "Video"
        }
      }
    }

    return if (totalWithSameName > 1) {
      val quality = media.quality
      if (quality != null) "$candidate – $quality" else "$candidate #${index + 1}"
    } else {
      candidate
    }
  }

  private fun isGenericPageTitle(title: String): Boolean {
    val lower = title.lowercase()
    return lower in setOf("loading...", "loading", "untitled", "home", "video player", "watch") ||
      lower.startsWith("http://") || lower.startsWith("https://")
  }
}
