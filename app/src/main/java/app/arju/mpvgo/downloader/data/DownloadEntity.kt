package app.arju.mpvgo.downloader.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import app.arju.mpvgo.downloader.engine.FailReason

enum class DownloadStatus {
  Queued,
  Running,
  Paused,
  Completed,
  Failed,
  ;

  val isActive: Boolean get() = this == Queued || this == Running
}

@Entity(tableName = "downloads", indices = [Index("status")])
data class DownloadEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val fileName: String,
  /** Relative to shared storage root, e.g. "Download/mpvGo". */
  val folder: String,
  val title: String? = null,
  val mimeType: String? = null,
  val totalBytes: Long = -1,
  val downloadedBytes: Long = 0,
  val status: DownloadStatus = DownloadStatus.Queued,
  val failReason: FailReason? = null,
  val retries: Int = 0,
  val isHls: Boolean = false,
  val hlsQuality: Int = 0,
  val segmentsDone: Int = 0,
  val segmentsTotal: Int = 0,
  val connections: Int = 8,
  val wifiOnly: Boolean = false,
  val userAgent: String? = null,
  val referer: String? = null,
  val cookies: String? = null,
  val sourcePageUrl: String? = null,
  val isPrivate: Boolean = false,
  val nameEdited: Boolean = false,
  /** Higher runs first; "Download" jumps ahead of "Add to queue". */
  val priority: Int = 0,
  val supportsResume: Boolean = false,
  /** [app.arju.mpvgo.downloader.engine.RangePlan.serialize] output, or HLS segment index. */
  val partsState: String? = null,
  val finalPath: String? = null,
  val finalUri: String? = null,
  val durationMs: Long = 0,
  val width: Int = 0,
  val height: Int = 0,
  val createdAt: Long = System.currentTimeMillis(),
  val completedAt: Long? = null,
) {
  val progress: Float
    get() = when {
      isHls && segmentsTotal > 0 -> segmentsDone.toFloat() / segmentsTotal
      totalBytes > 0 -> (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
      else -> 0f
    }

  fun headers(): Map<String, String> = buildMap {
    userAgent?.takeIf { it.isNotBlank() }?.let { put("User-Agent", it) }
    referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
    cookies?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
  }
}

class DownloadConverters {
  @TypeConverter fun statusToString(s: DownloadStatus): String = s.name

  @TypeConverter fun stringToStatus(s: String): DownloadStatus =
    runCatching { DownloadStatus.valueOf(s) }.getOrDefault(DownloadStatus.Paused)

  @TypeConverter fun reasonToString(r: FailReason?): String? = r?.name

  @TypeConverter fun stringToReason(s: String?): FailReason? = s?.let { runCatching { FailReason.valueOf(it) }.getOrNull() }
}
