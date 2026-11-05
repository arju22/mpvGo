package app.arju.mpvgo.player

import java.util.Locale

data class StreamQuality(
  val id: Int,
  val label: String,
  val height: Int = 0,
  val isAuto: Boolean = false,
  val isHd: Boolean = false,
  val bitrateText: String? = null,
  val isDataSaver: Boolean = false,
  val url: String? = null,
)

data class QualityState(
  val isOnlineStream: Boolean = false,
  val qualities: List<StreamQuality> = emptyList(),
  val selectedQuality: StreamQuality? = null,
  val activeHeight: Int = 0,
  val currentTimeText: String = "",
) {
  val hasMultipleQualities: Boolean
    get() = isOnlineStream && qualities.size > 1

  val chipText: String
    get() {
      val sel = selectedQuality
      return if (sel == null || sel.isAuto) {
        if (activeHeight > 0) "Auto · ${activeHeight}p" else "Auto"
      } else {
        sel.label
      }
    }

  val isHd: Boolean
    get() = if (selectedQuality == null || selectedQuality.isAuto) {
      activeHeight >= 720
    } else {
      selectedQuality.isHd
    }

  companion object {
    fun formatBitrate(bps: Long): String {
      if (bps <= 0L) return ""
      val mbps = bps.toDouble() / 1_000_000.0
      return if (mbps >= 1.0) {
        String.format(Locale.US, "%.1f Mbps", mbps)
      } else {
        val kbps = bps.toDouble() / 1_000.0
        String.format(Locale.US, "%.0f Kbps", kbps)
      }
    }

    fun buildQualitiesFromTracks(tracks: List<TrackNode>): List<StreamQuality> {
      val videoTracks = tracks.filter { it.type == "video" && it.albumArt != true && it.image != true }
      if (videoTracks.size <= 1) return emptyList()

      val autoOption = StreamQuality(
        id = -1,
        label = "Auto",
        isAuto = true,
      )

      val variants = videoTracks.map { track ->
        val h = track.demuxH?.toInt() ?: 0
        val bitrate = (track.hlsBitrate?.takeIf { it > 0 } ?: track.demuxBitrate?.takeIf { it > 0 }) ?: 0L
        val brText = if (bitrate > 0) formatBitrate(bitrate) else null
        val label = if (h > 0) "${h}p" else track.title ?: "Video"
        StreamQuality(
          id = track.id,
          label = label,
          height = h,
          isAuto = false,
          isHd = h >= 720,
          bitrateText = brText,
          isDataSaver = h > 0 && h <= 360,
        )
      }.sortedByDescending { it.height }

      return listOf(autoOption) + variants
    }
  }
}
