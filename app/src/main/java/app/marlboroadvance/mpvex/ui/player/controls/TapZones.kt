package app.marlboroadvance.mpvex.ui.player.controls

enum class TapRegion { Left, Center, Right }

enum class SingleTapAction { ShowControls, CenterAction, ToggleControls }

/** [region] drives double-tap actions (unchanged); [singleTap] is what a lone tap does. */
data class TapTarget(val region: TapRegion, val singleTap: SingleTapAction)

data class TapZoneConfig(
  val sideWidthPercent: Int = 35,
  val singleTapForCenter: Boolean = false,
  val ellipseWidthPercent: Int = TapZones.DEFAULT_SIZE,
  val ellipseHeightPercent: Int = TapZones.DEFAULT_SIZE,
  val centerXPercent: Int = TapZones.DEFAULT_CENTER,
  val centerYPercent: Int = TapZones.DEFAULT_CENTER,
  val bottomStripShowsControls: Boolean = true,
)

object TapZones {
  const val DEFAULT_SIZE = 80
  const val DEFAULT_CENTER = 50
  const val MIN_SIZE = 15
  const val MAX_SIZE = 100

  /** Fixed height of the bottom strip (seekbar + bottom button rows in landscape). */
  const val BOTTOM_STRIP_FRACTION = 0.25f

  fun region(x: Float, width: Float, sideWidthPercent: Int): TapRegion {
    val side = width * sideWidthPercent / 100f
    return when {
      x > width - side -> TapRegion.Right
      x < side -> TapRegion.Left
      else -> TapRegion.Center
    }
  }

  fun inBottomStrip(y: Float, height: Float): Boolean = y >= height * (1f - BOTTOM_STRIP_FRACTION)

  fun inEllipse(x: Float, y: Float, width: Float, height: Float, config: TapZoneConfig): Boolean {
    val rx = width * config.ellipseWidthPercent / 200f
    val ry = height * config.ellipseHeightPercent / 200f
    if (rx <= 0f || ry <= 0f) return false
    val dx = (x - width * config.centerXPercent / 100f) / rx
    val dy = (y - height * config.centerYPercent / 100f) / ry
    return dx * dx + dy * dy <= 1f
  }

  /**
   * Routing (single tap for center on): bottom strip shows controls, ellipse runs the center
   * action, anything else toggles controls. With it off every single tap toggles controls.
   */
  fun classifyTap(x: Float, y: Float, width: Float, height: Float, config: TapZoneConfig): TapTarget {
    val region = region(x, width, config.sideWidthPercent)
    val single = when {
      !config.singleTapForCenter -> SingleTapAction.ToggleControls
      config.bottomStripShowsControls && inBottomStrip(y, height) -> SingleTapAction.ShowControls
      inEllipse(x, y, width, height, config) -> SingleTapAction.CenterAction
      else -> SingleTapAction.ToggleControls
    }
    return TapTarget(region, single)
  }
}
