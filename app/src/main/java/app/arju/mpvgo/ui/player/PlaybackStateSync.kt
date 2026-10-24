package app.arju.mpvgo.ui.player

import kotlin.math.abs

/**
 * mpvGo: decides when the MediaSession playback state must be re-sent. The system extrapolates the
 * position from the last (position, updateTime, speed), so steady playback needs no updates; only a
 * seek, a stall or a speed change makes the real position drift away from the extrapolated one.
 */
object PlaybackStateSync {
  const val DRIFT_THRESHOLD_MS = 1_500L

  fun needsUpdate(
    lastPositionMs: Long,
    lastUpdateMs: Long,
    lastSpeed: Float,
    playing: Boolean,
    positionMs: Long,
    nowMs: Long,
    thresholdMs: Long = DRIFT_THRESHOLD_MS,
  ): Boolean {
    val expected =
      if (playing) lastPositionMs + ((nowMs - lastUpdateMs) * lastSpeed).toLong() else lastPositionMs
    return abs(positionMs - expected) > thresholdMs
  }
}
