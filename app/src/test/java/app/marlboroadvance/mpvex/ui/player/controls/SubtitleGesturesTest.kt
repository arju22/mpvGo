package app.marlboroadvance.mpvex.ui.player.controls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleGesturesTest {
  private val w = 2400f
  private val h = 1080f

  @Test
  fun bandSitsAboveBottomMarginAndContainsTheText() {
    val band = SubtitleGestures.estimateBand(w, h, 100, 22, 55, 1f, "Hello there", 0f)
    assertNotNull(band)
    band!!
    // bottom = (1080 - 22*1.5) = 1047, one line of 55*1.5*1.25 px
    assertEquals(1047f, band.bottom, 0.5f)
    assertEquals(1047f - 103.125f, band.top, 0.5f)
    assertTrue(band.contains(w / 2, 1000f))
    assertFalse(band.contains(100f, 1000f))
    assertFalse(band.contains(w / 2, 500f))
  }

  @Test
  fun bandGrowsWithLinesWrappingAndScale() {
    val one = SubtitleGestures.estimateBand(w, h, 100, 22, 55, 1f, "a", 0f)!!
    val two = SubtitleGestures.estimateBand(w, h, 100, 22, 55, 1f, "a\nb", 0f)!!
    val wrapped = SubtitleGestures.estimateBand(w, h, 100, 22, 55, 1f, "x".repeat(200), 0f)!!
    val big = SubtitleGestures.estimateBand(w, h, 100, 22, 55, 2f, "a", 0f)!!
    val oneHeight = one.bottom - one.top
    assertEquals(oneHeight * 2, two.bottom - two.top, 0.5f)
    assertTrue(wrapped.bottom - wrapped.top > oneHeight * 2)
    assertTrue(wrapped.right <= w && wrapped.left >= 0f)
    assertEquals(oneHeight * 2, big.bottom - big.top, 0.5f)
  }

  @Test
  fun noBandWithoutText() {
    assertNull(SubtitleGestures.estimateBand(w, h, 100, 22, 55, 1f, "", 10f))
    assertNull(SubtitleGestures.estimateBand(w, h, 100, 22, 55, 1f, null, 10f))
    assertNull(SubtitleGestures.estimateBand(0f, h, 100, 22, 55, 1f, "a", 10f))
  }

  @Test
  fun paddingIsClampedToTheView() {
    val band = SubtitleGestures.estimateBand(w, h, 100, 0, 55, 1f, "a", 100f)!!
    assertEquals(h, band.bottom, 0f)
  }

  @Test
  fun dragModeWaitsThenPicksDominantAxis() {
    assertEquals(SubtitleDragMode.None, SubtitleGestures.dragMode(5f, 5f, 12f))
    assertEquals(SubtitleDragMode.Seek, SubtitleGestures.dragMode(-20f, 5f, 12f))
    assertEquals(SubtitleDragMode.Move, SubtitleGestures.dragMode(4f, -20f, 12f))
  }

  @Test
  fun swipeLeftIsNextRightIsPrevious() {
    assertEquals(1, SubtitleGestures.subSeekStep(-150f, 120f))
    assertEquals(-1, SubtitleGestures.subSeekStep(150f, 120f))
    assertEquals(0, SubtitleGestures.subSeekStep(60f, 120f))
  }

  @Test
  fun dragMovesSubPosProportionallyAndClamps() {
    val usable = h - 22 * h / 720f
    assertEquals(90, SubtitleGestures.draggedSubPos(100, -usable * 0.1f, h, 22))
    assertEquals(110, SubtitleGestures.draggedSubPos(100, usable * 0.1f, h, 22))
    assertEquals(0, SubtitleGestures.draggedSubPos(10, -h * 2, h, 22))
    assertEquals(150, SubtitleGestures.draggedSubPos(140, h * 2, h, 22))
  }

  @Test
  fun pinchScalesByDistanceRatioAndClamps() {
    assertEquals(2f, SubtitleGestures.pinchedScale(1f, 100f, 200f), 0.001f)
    assertEquals(0.5f, SubtitleGestures.pinchedScale(1f, 200f, 100f), 0.001f)
    assertEquals(SubtitleGestures.MAX_SCALE, SubtitleGestures.pinchedScale(3f, 100f, 1000f), 0.001f)
    assertEquals(SubtitleGestures.MIN_SCALE, SubtitleGestures.pinchedScale(1f, 1000f, 10f), 0.001f)
    assertEquals(1.2f, SubtitleGestures.pinchedScale(1.2f, 0f, 100f), 0.001f)
  }

  @Test
  fun liftKeepsSubtitleBottomAboveControls() {
    val controlsTop = 800f
    val lifted = SubtitleGestures.liftedSubPos(100, controlsTop, h, 22, 16f)
    assertTrue(lifted < 100)
    assertTrue(SubtitleGestures.subtitleBottom(h, lifted, 22) <= controlsTop - 16f)
    assertTrue(SubtitleGestures.subtitleBottom(h, lifted + 1, 22) > controlsTop - 16f)
    // Already above the controls: unchanged
    assertEquals(50, SubtitleGestures.liftedSubPos(50, controlsTop, h, 22, 16f))
    // Controls at the very top never give a negative position
    assertEquals(0, SubtitleGestures.liftedSubPos(100, 0f, h, 22, 16f))
  }
}
