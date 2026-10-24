package app.arju.mpvgo.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TapZonesTest {
  private val w = 2000f
  private val h = 1000f
  private val on = TapZoneConfig(sideWidthPercent = 20, singleTapForCenter = true)

  private fun tap(x: Float, y: Float, config: TapZoneConfig = on) = TapZones.classifyTap(x, y, w, h, config)

  @Test
  fun `bottom strip wins over the ellipse and sides`() {
    assertEquals(TapTarget(TapRegion.Center, SingleTapAction.ShowControls), tap(1000f, 800f))
    assertEquals(TapTarget(TapRegion.Left, SingleTapAction.ShowControls), tap(10f, 990f))
    assertEquals(SingleTapAction.ShowControls, tap(1000f, 750f).singleTap)
    assertEquals(SingleTapAction.CenterAction, tap(1000f, 749f).singleTap)
  }

  @Test
  fun `bottom strip switch off falls back to ellipse or toggle`() {
    val noStrip = on.copy(bottomStripShowsControls = false)
    assertEquals(SingleTapAction.CenterAction, tap(1000f, 800f, noStrip).singleTap)
    assertEquals(SingleTapAction.ToggleControls, tap(1000f, 999f, noStrip).singleTap)
  }

  @Test
  fun `inside and outside the ellipse`() {
    assertEquals(TapTarget(TapRegion.Center, SingleTapAction.CenterAction), tap(1000f, 500f))
    // 80% x 80% ellipse centered: x radius 800, y radius 400
    assertEquals(SingleTapAction.CenterAction, tap(1000f, 101f).singleTap)
    assertEquals(SingleTapAction.ToggleControls, tap(1000f, 99f).singleTap)
    assertEquals(SingleTapAction.ToggleControls, tap(1700f, 150f).singleTap)
  }

  @Test
  fun `ellipse overlapping a side keeps the side region for double tap`() {
    assertEquals(TapTarget(TapRegion.Left, SingleTapAction.CenterAction), tap(300f, 500f))
    assertEquals(TapTarget(TapRegion.Right, SingleTapAction.CenterAction), tap(1700f, 500f))
  }

  @Test
  fun `edge pixels are handled`() {
    assertEquals(TapTarget(TapRegion.Left, SingleTapAction.ToggleControls), tap(0f, 0f))
    assertEquals(TapTarget(TapRegion.Right, SingleTapAction.ToggleControls), tap(w - 1, 0f))
    assertEquals(TapTarget(TapRegion.Right, SingleTapAction.ShowControls), tap(w - 1, h - 1))
  }

  @Test
  fun `single tap for center off always toggles`() {
    val off = on.copy(singleTapForCenter = false)
    assertEquals(TapTarget(TapRegion.Center, SingleTapAction.ToggleControls), tap(1000f, 500f, off))
    assertEquals(TapTarget(TapRegion.Center, SingleTapAction.ToggleControls), tap(1000f, 990f, off))
  }

  @Test
  fun `moved and resized ellipse`() {
    val c = on.copy(ellipseWidthPercent = 20, ellipseHeightPercent = 20, centerXPercent = 25, centerYPercent = 30)
    assertTrue(TapZones.inEllipse(500f, 300f, w, h, c))
    assertFalse(TapZones.inEllipse(1000f, 500f, w, h, c))
    assertTrue(TapZones.inEllipse(699f, 300f, w, h, c))
    assertFalse(TapZones.inEllipse(701f, 300f, w, h, c))
  }
}
