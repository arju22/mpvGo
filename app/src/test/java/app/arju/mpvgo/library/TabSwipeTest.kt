package app.arju.mpvgo.library

import org.junit.Assert.assertEquals
import org.junit.Test

class TabSwipeTest {
  private fun dir(dx: Float, v: Float) = tabSwipeDirection(dx, v, distanceThreshold = 100f, velocityThreshold = 1000f)

  @Test fun longSwipeLeftGoesToNextTab() = assertEquals(1, dir(-150f, 0f))

  @Test fun longSwipeRightGoesToPreviousTab() = assertEquals(-1, dir(150f, 0f))

  @Test fun shortSlowDragDoesNothing() = assertEquals(0, dir(-40f, -200f))

  @Test fun shortFastFlickSwitches() {
    assertEquals(1, dir(-40f, -1500f))
    assertEquals(-1, dir(40f, 1500f))
  }

  @Test fun flickAgainstDragDirectionDoesNothing() = assertEquals(0, dir(-40f, 1500f))
}
