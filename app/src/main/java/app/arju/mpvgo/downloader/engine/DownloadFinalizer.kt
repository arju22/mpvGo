package app.arju.mpvgo.downloader.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import app.arju.mpvgo.core.utils.media.MediaLibraryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Moves a finished file into shared storage and indexes it right away so the library shows it. */
class DownloadFinalizer(private val context: Context) {

  data class Placed(val path: String?, val uri: Uri?, val durationMs: Long = 0, val width: Int = 0, val height: Int = 0)

  suspend fun place(source: File, folder: String, fileName: String): Placed = withContext(Dispatchers.IO) {
    val mime = mimeOf(fileName)
    val meta = probe(source)
    val placed = if (Environment.isExternalStorageManager()) {
      placeDirect(source, folder, fileName, mime)
    } else {
      placeMediaStore(source, folder, fileName, mime)
    }
    MediaLibraryEvents.notifyChanged()
    placed.copy(durationMs = meta[0].toLong(), width = meta[1].toInt(), height = meta[2].toInt())
  }

  private fun probe(file: File): LongArray {
    val r = android.media.MediaMetadataRetriever()
    return try {
      r.setDataSource(file.absolutePath)
      val d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
      var w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
      var h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
      val rot = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
      if (rot == 90 || rot == 270) w = h.also { h = w }
      longArrayOf(d, w, h)
    } catch (_: Throwable) {
      longArrayOf(0, 0, 0)
    } finally {
      runCatching { r.release() }
    }
  }

  private suspend fun placeDirect(source: File, folder: String, fileName: String, mime: String): Placed {
    val dir = File(Environment.getExternalStorageDirectory(), folder).apply { mkdirs() }
    val target = File(dir, DownloadNaming.unique(fileName) { File(dir, it).exists() })
    if (!source.renameTo(target)) {
      source.inputStream().use { i -> target.outputStream().use { o -> i.copyTo(o, 256 * 1024) } }
      source.delete()
    }
    val uri = scan(target.absolutePath, mime)
    return Placed(target.absolutePath, uri)
  }

  private fun placeMediaStore(source: File, folder: String, fileName: String, mime: String): Placed {
    val resolver = context.contentResolver
    val collection = if (folder.startsWith(Environment.DIRECTORY_MOVIES)) {
      MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
      MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
    val values = ContentValues().apply {
      put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
      put(MediaStore.MediaColumns.MIME_TYPE, mime)
      put(MediaStore.MediaColumns.RELATIVE_PATH, folder.trimEnd('/') + "/")
      put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, values) ?: throw java.io.IOException("MediaStore insert failed")
    try {
      resolver.openOutputStream(uri)?.use { o -> source.inputStream().use { it.copyTo(o, 256 * 1024) } }
        ?: throw java.io.IOException("Can't open output")
      resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    } catch (t: Throwable) {
      resolver.delete(uri, null, null)
      throw t
    }
    source.delete()
    @Suppress("DEPRECATION")
    val path = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
      if (c.moveToFirst()) c.getString(0) else null
    }
    return Placed(path, uri)
  }

  private suspend fun scan(path: String, mime: String): Uri? = suspendCancellableCoroutine { cont ->
    MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf(mime)) { _, uri -> if (cont.isActive) cont.resume(uri) }
  }

  /** Removes a finished download's file from storage. */
  suspend fun delete(path: String?, uri: String?) = withContext(Dispatchers.IO) {
    runCatching { uri?.let { context.contentResolver.delete(Uri.parse(it), null, null) } }
    path?.let { p ->
      val f = File(p)
      if (f.exists() && f.delete()) MediaScannerConnection.scanFile(context, arrayOf(p), null, null)
    }
    MediaLibraryEvents.notifyChanged()
  }

  companion object {
    fun mimeOf(fileName: String): String =
      MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
        ?: when (fileName.substringAfterLast('.').lowercase()) {
          "ts" -> "video/mp2t"
          "mkv" -> "video/x-matroska"
          else -> "video/mp4"
        }
  }
}
