package app.arju.mpvgo.utils.media

import android.content.Context
import android.media.MediaScannerConnection
import android.provider.MediaStore
import android.util.Log
import app.arju.mpvgo.utils.storage.FileTypeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * mpvGo: pull-to-refresh helper. Instead of re-scanning every file (or the whole storage), hand
 * MediaScanner only the videos in one folder that MediaStore doesn't know about yet.
 */
object MissingMediaScan {
  private const val TAG = "MissingMediaScan"

  internal fun missing(
    onDisk: List<String>,
    indexed: Set<String>,
  ): List<String> = onDisk.filterNot { it in indexed }

  /** Returns the number of files handed to MediaScanner; waits for them up to [timeoutMs]. */
  suspend fun indexMissingVideos(
    context: Context,
    folderPath: String,
    timeoutMs: Long = 5_000,
  ): Int =
    withContext(Dispatchers.IO) {
      val folder = File(folderPath)
      if (!folder.isDirectory) return@withContext 0
      val onDisk = folder
        .listFiles { f -> f.isFile && FileTypeUtils.isVideoFile(f) }
        ?.map { it.absolutePath }
        .orEmpty()
      if (onDisk.isEmpty()) return@withContext 0

      val todo = missing(onDisk, indexedVideoPaths(context, folder.absolutePath))
      if (todo.isEmpty()) return@withContext 0

      Log.d(TAG, "Indexing ${todo.size} new video(s) in $folderPath")
      withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
          val left = AtomicInteger(todo.size)
          MediaScannerConnection.scanFile(context, todo.toTypedArray(), null) { _, _ ->
            if (left.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
          }
        }
      }
      todo.size
    }

  private fun indexedVideoPaths(
    context: Context,
    folderPath: String,
  ): Set<String> {
    val folder = folderPath.trimEnd('/')
    val result = HashSet<String>()
    runCatching {
      context.contentResolver.query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.Video.Media.DATA),
        "${MediaStore.Video.Media.DATA} LIKE ?",
        arrayOf("$folder/%"),
        null,
      )?.use { c ->
        val col = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
        while (c.moveToNext()) c.getString(col)?.let(result::add)
      }
    }.onFailure { Log.w(TAG, "MediaStore query failed for $folderPath", it) }
    return result
  }
}
