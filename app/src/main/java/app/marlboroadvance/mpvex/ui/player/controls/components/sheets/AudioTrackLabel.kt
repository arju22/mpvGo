package app.marlboroadvance.mpvex.ui.player.controls.components.sheets

import app.marlboroadvance.mpvex.ui.player.TrackNode
import java.util.Locale

/**
 * Readable audio track label, e.g. "#2: Hindi · AAC 5.1" or "#1: Director's commentary · English · Opus Stereo".
 * Pure function so it can be unit tested on the JVM.
 */
object AudioTrackLabel {
  private val hiddenLangs = setOf("und", "unk", "zxx", "qaa")

  // ISO 639-2/B codes that java.util.Locale doesn't resolve to a name.
  private val bibliographic = mapOf(
    "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs",
    "dut" to "nl", "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is",
    "mac" to "mk", "mao" to "mi", "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk",
    "tib" to "bo", "wel" to "cy",
  )

  fun format(track: TrackNode, displayLocale: Locale = Locale.getDefault()): String {
    val title = track.title?.trim().orEmpty()
    val language = languageName(track.lang, displayLocale)
    val tech = listOfNotNull(codecName(track.codec), channelsName(track.demuxChannels, track.demuxChannelCount ?: track.audioChannels))
      .joinToString(" ")
      .ifEmpty { null }

    val showLanguage = language != null &&
      !title.contains(language, ignoreCase = true) &&
      !title.equals(track.lang?.trim(), ignoreCase = true)
    val parts = listOfNotNull(
      title.ifEmpty { null },
      language?.takeIf { showLanguage },
      tech,
    )
    return if (parts.isEmpty()) "Audio #${track.id}" else "#${track.id}: ${parts.joinToString(" · ")}"
  }

  fun languageName(lang: String?, displayLocale: Locale): String? {
    val code = lang?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
    val primary = code.substringBefore('-').lowercase(Locale.ROOT)
    if (primary in hiddenLangs) return null
    val tag = bibliographic[primary]?.let { code.replaceRange(0, primary.length, it) } ?: code
    val name = Locale.forLanguageTag(tag).getDisplayLanguage(displayLocale)
    if (name.isBlank() || name.equals(primary, ignoreCase = true)) return code
    return name.replaceFirstChar { it.titlecase(displayLocale) }
  }

  fun codecName(codec: String?): String? {
    val raw = codec?.lowercase(Locale.ROOT)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return when {
      raw.contains("aac") -> "AAC"
      raw.contains("opus") -> "Opus"
      raw.contains("eac3") || raw.contains("ec-3") -> "E-AC-3"
      raw.contains("ac3") -> "AC-3"
      raw.contains("dts") -> "DTS"
      raw.contains("truehd") -> "TrueHD"
      raw.contains("flac") -> "FLAC"
      raw.contains("vorbis") -> "Vorbis"
      raw.contains("mp3") -> "MP3"
      raw.contains("alac") -> "ALAC"
      raw.startsWith("pcm") -> "PCM"
      else -> raw.uppercase(Locale.ROOT)
    }
  }

  fun channelsName(layout: String?, count: Long?): String? {
    val raw = layout?.lowercase(Locale.ROOT)?.trim().orEmpty()
    when {
      raw == "mono" -> return "Mono"
      raw == "stereo" -> return "Stereo"
      Regex("^\\d\\.\\d").containsMatchIn(raw) -> return raw.substring(0, 3)
    }
    return when (count) {
      null, 0L -> null
      1L -> "Mono"
      2L -> "Stereo"
      3L -> "2.1"
      6L -> "5.1"
      8L -> "7.1"
      else -> "$count ch"
    }
  }
}
