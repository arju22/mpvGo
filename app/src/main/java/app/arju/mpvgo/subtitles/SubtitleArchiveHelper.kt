package app.arju.mpvgo.subtitles

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object SubtitleArchiveHelper {
  private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub")

  /**
   * Extracts subtitle bytes from raw downloaded data (which may be a ZIP archive, GZ stream, or plain text).
   * Returns a pair of (extractedFileName, utf8StringContent), or null if extraction fails.
   */
  fun extractSubtitleText(data: ByteArray, originalName: String): Pair<String, String>? {
    if (data.isEmpty()) return null

    // Check for ZIP magic bytes (PK\x03\x04)
    if (data.size >= 4 && data[0] == 0x50.toByte() && data[1] == 0x4B.toByte() && data[2] == 0x03.toByte() && data[3] == 0x04.toByte()) {
      return extractFromZip(data, originalName)
    }

    // Check for GZIP magic bytes (1f 8b)
    if (data.size >= 2 && data[0] == 0x1F.toByte() && data[1] == 0x8B.toByte()) {
      return extractFromGzip(data, originalName)
    }

    // Plain text file
    val text = toUtf8String(data)
    val name = if (originalName.contains('.')) originalName else "$originalName.srt"
    return name to text
  }

  private fun extractFromZip(data: ByteArray, fallbackName: String): Pair<String, String>? {
    return runCatching {
      ZipInputStream(ByteArrayInputStream(data)).use { zis ->
        var entry = zis.nextEntry
        var bestName: String? = null
        var bestBytes: ByteArray? = null

        while (entry != null) {
          val name = entry.name
          val ext = name.substringAfterLast('.', "").lowercase()
          if (!entry.isDirectory && ext in SUBTITLE_EXTENSIONS) {
            val out = ByteArrayOutputStream()
            zis.copyTo(out)
            bestName = name.substringAfterLast('/')
            bestBytes = out.toByteArray()
            break
          }
          entry = zis.nextEntry
        }

        if (bestBytes != null && bestName != null) {
          bestName to toUtf8String(bestBytes)
        } else {
          null
        }
      }
    }.getOrNull()
  }

  private fun extractFromGzip(data: ByteArray, originalName: String): Pair<String, String>? {
    return runCatching {
      GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
        val out = ByteArrayOutputStream()
        gis.copyTo(out)
        val extractedBytes = out.toByteArray()
        val name = originalName.removeSuffix(".gz")
        val cleanName = if (name.contains('.')) name else "$name.srt"
        cleanName to toUtf8String(extractedBytes)
      }
    }.getOrNull()
  }

  fun toUtf8String(bytes: ByteArray): String {
    // Check UTF-8 BOM (EF BB BF)
    val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) 3 else 0

    // Try parsing as UTF-8 with strict decoder
    val decoder = StandardCharsets.UTF_8.newDecoder()
    val decoded = runCatching {
      val bb = java.nio.ByteBuffer.wrap(bytes, start, bytes.size - start)
      decoder.decode(bb).toString()
    }.getOrNull()

    if (decoded != null) return decoded

    // Fallback: Windows-1252 / ISO-8859-1
    return String(bytes, start, bytes.size - start, Charset.forName("windows-1252"))
  }
}
