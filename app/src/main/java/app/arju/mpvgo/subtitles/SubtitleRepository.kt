package app.arju.mpvgo.subtitles

import android.content.Context
import android.net.Uri
import android.util.Log
import app.arju.mpvgo.core.preferences.SubtitlesPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class SubtitleRepository(
  private val context: Context,
  private val prefs: SubtitlesPreferences,
) {
  private val http by lazy {
    OkHttpClient.Builder()
      .connectTimeout(15, TimeUnit.SECONDS)
      .readTimeout(30, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }

  val openSubtitles = OpenSubtitlesProvider(http, prefs)
  val subDl = SubDlProvider(http, prefs)
  val podnapisi = PodnapisiProvider(http)

  private val providers: List<SubtitleProvider>
    get() = listOf(openSubtitles, subDl, podnapisi)

  /**
   * Searches for online subtitles matching [query] across all configured providers.
   * Returns exact matches first, followed by results sorted by rating and download count.
   */
  suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
    coroutineScope {
      val deferred = providers.map { provider ->
        async {
          runCatching { provider.search(query) }.getOrDefault(emptyList())
        }
      }
      val all = deferred.awaitAll().flatten()
      // Sort exact matches first, then downloads and ratings
      all.sortedWith(
        compareByDescending<OnlineSubtitle> { it.isExactMatch }
          .thenByDescending { it.downloadCount }
          .thenByDescending { it.rating }
      )
    }
  }

  /**
   * Downloads [subtitle], saves it to disk (in subtitle save folder or next to [videoPath]),
   * and returns the resulting [File], or null on failure.
   */
  suspend fun downloadAndSave(
    subtitle: OnlineSubtitle,
    videoPath: String?,
  ): File? = withContext(Dispatchers.IO) {
    val provider = providers.find { it.name == subtitle.provider } ?: return@withContext null
    val extracted = provider.download(subtitle) ?: return@withContext null
    val (name, text) = extracted

    val targetDir = resolveTargetDirectory(videoPath)
    targetDir.mkdirs()

    val cleanName = sanitizeFileName(name)
    val destFile = File(targetDir, cleanName)

    runCatching {
      FileOutputStream(destFile).use { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
      }
      destFile
    }.onFailure {
      Log.e("SubtitleRepository", "Failed to save subtitle file: ${it.message}")
    }.getOrNull()
  }

  private fun resolveTargetDirectory(videoPath: String?): File {
    val saveFolder = prefs.subtitleSaveFolder.get().trim()
    if (saveFolder.isNotBlank()) {
      val folder = File(saveFolder)
      if (folder.exists() && folder.isDirectory && folder.canWrite()) {
        return folder
      }
    }

    if (!videoPath.isNullOrBlank() && (videoPath.startsWith("/") || videoPath.startsWith("file://"))) {
      val localPath = videoPath.removePrefix("file://")
      val videoFile = File(localPath)
      val parent = videoFile.parentFile
      if (parent != null && parent.exists() && parent.canWrite()) {
        return parent
      }
    }

    // Default fallback to app files
    return File(context.filesDir, "subtitles")
  }

  companion object {
    fun sanitizeFileName(raw: String): String {
      val cleaned = raw.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
      return if (cleaned.isBlank()) "subtitle.srt" else cleaned
    }
  }
}
