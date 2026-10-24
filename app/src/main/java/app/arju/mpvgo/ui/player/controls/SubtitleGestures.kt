package app.arju.mpvgo.ui.player.controls

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Estimated on-screen area of the current subtitle, in view pixels. */
data class SubtitleBand(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

enum class SubtitleDragMode { None, Seek, Move }

/**
 * Geometry for MX-style subtitle gestures and for keeping subtitles above the player controls.
 *
 * mpv doesn't expose where it drew the subtitle, so the band is estimated from the same
 * options mpv uses: font size is in pixels on a 720px-tall screen, and sub-pos places the
 * subtitle's bottom edge at that percentage of the height above the bottom margin.
 */
object SubtitleGestures {
  private const val REFERENCE_HEIGHT = 720f
  private const val LINE_HEIGHT = 1.25f
  private const val GLYPH_WIDTH = 0.5f
  private const val MAX_LINE_WIDTH = 0.95f
  const val MIN_POS = 0
  const val MAX_POS = 150
  const val MIN_SCALE = 0.25f
  const val MAX_SCALE = 5f

  private fun usableHeight(viewHeight: Float, marginY: Int): Float =
    viewHeight - marginY * viewHeight / REFERENCE_HEIGHT

  fun subtitleBottom(viewHeight: Float, subPos: Int, marginY: Int): Float =
    (usableHeight(viewHeight, marginY) * subPos / 100f).coerceIn(0f, viewHeight)

  fun estimateBand(
    viewWidth: Float,
    viewHeight: Float,
    subPos: Int,
    marginY: Int,
    fontSize: Int,
    subScale: Float,
    text: String?,
    padding: Float,
  ): SubtitleBand? {
    if (text.isNullOrBlank() || viewWidth <= 0f || viewHeight <= 0f) return null
    val fontPx = fontSize * subScale * viewHeight / REFERENCE_HEIGHT
    val maxLineWidth = viewWidth * MAX_LINE_WIDTH
    var lines = 0
    var widest = 0f
    text.trim().lines().forEach { line ->
      val width = line.length * fontPx * GLYPH_WIDTH
      lines += max(1, ceil(width / maxLineWidth).toInt())
      widest = max(widest, min(width, maxLineWidth))
    }
    val bottom = subtitleBottom(viewHeight, subPos, marginY)
    val top = bottom - lines * fontPx * LINE_HEIGHT
    val centerX = viewWidth / 2f
    return SubtitleBand(
      left = (centerX - widest / 2f - padding).coerceAtLeast(0f),
      top = (top - padding).coerceAtLeast(0f),
      right = (centerX + widest / 2f + padding).coerceAtMost(viewWidth),
      bottom = (bottom + padding).coerceAtMost(viewHeight),
    )
  }

  /** Decides what a one-finger drag that started on the subtitle does, once it has moved far enough. */
  fun dragMode(dx: Float, dy: Float, decideDistance: Float): SubtitleDragMode = when {
    hypot(dx, dy) <= decideDistance -> SubtitleDragMode.None
    abs(dx) > abs(dy) -> SubtitleDragMode.Seek
    else -> SubtitleDragMode.Move
  }

  /** Swipe left = next line (+1), swipe right = previous line (-1), like turning a page. */
  fun subSeekStep(dx: Float, threshold: Float): Int = when {
    dx <= -threshold -> 1
    dx >= threshold -> -1
    else -> 0
  }

  fun draggedSubPos(startPos: Int, dy: Float, viewHeight: Float, marginY: Int): Int {
    val usable = usableHeight(viewHeight, marginY)
    if (usable <= 0f) return startPos
    return (startPos + dy / usable * 100f).roundToInt().coerceIn(MIN_POS, MAX_POS)
  }

  fun pinchedScale(startScale: Float, startDistance: Float, distance: Float): Float {
    if (startDistance <= 0f) return startScale
    val scale = (startScale * distance / startDistance).coerceIn(MIN_SCALE, MAX_SCALE)
    return (scale * 100f).roundToInt() / 100f
  }

  /** The sub-pos to show while controls whose top edge is at [controlsTop] are visible. */
  fun liftedSubPos(userPos: Int, controlsTop: Float, viewHeight: Float, marginY: Int, gap: Float): Int {
    val usable = usableHeight(viewHeight, marginY)
    if (usable <= 0f) return userPos
    val ceiling = floor((controlsTop - gap) / usable * 100f).toInt().coerceAtLeast(MIN_POS)
    return min(userPos, ceiling)
  }
}
