package app.arju.mpvgo.webbrowser.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class IconCandidate(val url: String, val size: Int)

class FaviconStore(private val context: Context) {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val cacheDir = File(context.filesDir, "favicons")
  private val memCache = ConcurrentHashMap<String, Bitmap>()
  private val failedFetches = ConcurrentHashMap<String, Long>()
  private val liveFlows = ConcurrentHashMap<String, MutableStateFlow<Bitmap?>>()

  private val http by lazy {
    OkHttpClient.Builder()
      .connectTimeout(10, TimeUnit.SECONDS)
      .readTimeout(15, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }

  init {
    cacheDir.mkdirs()
  }

  fun get(host: String): Bitmap? {
    val clean = cleanHost(host)
    if (clean.isBlank()) return null
    memCache[clean]?.let { return it }
    val file = iconFile(clean)
    if (file.exists() && file.length() > 0) {
      val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
      if (bmp != null) {
        memCache[clean] = bmp
        return bmp
      }
    }
    return null
  }

  fun flowFor(host: String): StateFlow<Bitmap?> {
    val clean = cleanHost(host)
    return liveFlows.getOrPut(clean) {
      MutableStateFlow(get(clean))
    }.asStateFlow()
  }

  fun save(host: String, bitmap: Bitmap) {
    val clean = cleanHost(host)
    if (clean.isBlank() || bitmap.isRecycled) return
    val scaled = if (bitmap.width > 96 || bitmap.height > 96) {
      val maxDim = maxOf(bitmap.width, bitmap.height)
      val scale = 96f / maxDim
      val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
      val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
      Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    } else {
      bitmap
    }

    memCache[clean] = scaled
    liveFlows[clean]?.value = scaled

    scope.launch {
      runCatching {
        val file = iconFile(clean)
        FileOutputStream(file).use { out ->
          scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        enforceQuota()
      }
    }
  }

  fun fetchIfNeeded(urlOrHost: String) {
    val clean = cleanHost(urlOrHost)
    if (clean.isBlank()) return
    val existing = iconFile(clean)
    val now = System.currentTimeMillis()
    if (existing.exists() && existing.length() > 0) {
      if (now - existing.lastModified() < 30L * 24 * 60 * 60 * 1000) {
        // Less than 30 days old, fresh
        return
      }
    }
    val lastFailed = failedFetches[clean] ?: 0L
    if (now - lastFailed < 7L * 24 * 60 * 60 * 1000) {
      // Failed within 7 days, back off
      return
    }

    scope.launch {
      val bmp = fetchDirect(clean)
      if (bmp != null) {
        save(clean, bmp)
      } else {
        failedFetches[clean] = now
      }
    }
  }

  private suspend fun fetchDirect(host: String): Bitmap? = withContext(Dispatchers.IO) {
    val homeUrl = "https://$host/"
    // Step 1: fetch HTML and look for link tags
    val html = runCatching {
      val req = Request.Builder()
        .url(homeUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
        .build()
      http.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) resp.body?.string()?.take(65536) else null
      }
    }.getOrNull()

    val candidateUrls = if (html != null) {
      val candidates = parseIconLinks(html, homeUrl)
      candidates.sortedByDescending { it.size }.map { it.url }
    } else {
      emptyList()
    }

    // Fallbacks
    val fallbacks = listOf(
      "https://$host/apple-touch-icon.png",
      "https://$host/favicon.ico",
      "http://$host/favicon.ico"
    )
    val allToTry = (candidateUrls + fallbacks).distinct()

    for (url in allToTry) {
      val bmp = downloadImage(url)
      if (bmp != null) return@withContext bmp
    }
    null
  }

  private fun downloadImage(url: String): Bitmap? {
    return runCatching {
      val req = Request.Builder().url(url).build()
      http.newCall(req).execute().use { resp ->
        if (resp.isSuccessful) {
          val bytes = resp.body?.bytes() ?: return@use null
          BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
          null
        }
      }
    }.getOrNull()
  }

  fun fetchForHost(host: String) = fetchIfNeeded(host)

  fun clearAll() = clear()

  fun clear() {
    memCache.clear()
    liveFlows.values.forEach { it.value = null }
    failedFetches.clear()
    scope.launch {
      runCatching {
        cacheDir.listFiles()?.forEach { it.delete() }
      }
    }
  }

  private fun iconFile(cleanHost: String): File = File(cacheDir, "$cleanHost.png")

  private fun enforceQuota() {
    val files = cacheDir.listFiles() ?: return
    var totalSize = files.sumOf { it.length() }
    val maxBytes = 5L * 1024 * 1024 // 5 MB
    val targetBytes = 4L * 1024 * 1024 // 4 MB
    if (totalSize > maxBytes) {
      val sorted = files.sortedBy { it.lastModified() }
      for (f in sorted) {
        val len = f.length()
        if (f.delete()) {
          totalSize -= len
          if (totalSize <= targetBytes) break
        }
      }
    }
  }

  companion object {
    fun cleanHost(raw: String): String {
      val withoutScheme = raw.substringAfter("://").substringBefore('/').substringBefore('?')
      val host = withoutScheme.substringBefore(':').lowercase().trim()
      return host.removePrefix("www.")
    }

    fun hostColor(host: String): Color {
      val hash = abs(host.hashCode())
      val hue = (hash % 360).toFloat()
      val saturation = 0.55f
      val value = 0.80f
      return Color.hsv(hue, saturation, value)
    }

    private val LINK_REGEX = Regex("""<link\s+[^>]*>""", RegexOption.IGNORE_CASE)
    private val REL_REGEX = Regex("""rel\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val HREF_REGEX = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val SIZES_REGEX = Regex("""sizes\s*=\s*["'](\d+)x(\d+)["']""", RegexOption.IGNORE_CASE)

    fun parseIconLinks(html: String, baseUrl: String): List<IconCandidate> {
      val results = mutableListOf<IconCandidate>()
      for (match in LINK_REGEX.findAll(html)) {
        val tag = match.value
        val rel = REL_REGEX.find(tag)?.groupValues?.get(1)?.lowercase() ?: continue
        if (!rel.contains("icon")) continue

        val href = HREF_REGEX.find(tag)?.groupValues?.get(1)?.trim() ?: continue
        if (href.startsWith("data:")) continue

        val sizeMatch = SIZES_REGEX.find(tag)
        val size = if (sizeMatch != null) {
          sizeMatch.groupValues[1].toIntOrNull() ?: 32
        } else if (rel.contains("apple-touch-icon")) {
          180
        } else {
          32
        }

        val resolved = resolveUrl(baseUrl, href)
        results.add(IconCandidate(resolved, size))
      }
      return results
    }

    fun resolveUrl(base: String, href: String): String {
      if (href.startsWith("http://") || href.startsWith("https://")) return href
      val baseClean = base.substringBefore('?').substringBefore('#')
      if (href.startsWith("//")) {
        val scheme = if (baseClean.startsWith("https")) "https:" else "http:"
        return scheme + href
      }
      val hostPart = baseClean.substringBefore('/', "") + "//" + baseClean.substringAfter("://").substringBefore('/')
      return if (href.startsWith("/")) {
        hostPart + href
      } else {
        baseClean.substringBeforeLast('/') + "/" + href
      }
    }
  }
}
