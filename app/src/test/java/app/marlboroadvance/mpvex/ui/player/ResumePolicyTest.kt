package app.marlboroadvance.mpvex.ui.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePolicyTest {
  @Test
  fun `mid-video position is kept`() {
    assertEquals(18, ResumePolicy.resumePosition(lastPosition = 18, timeRemaining = 40))
  }

  @Test
  fun `no saved position starts at zero`() {
    assertEquals(0, ResumePolicy.resumePosition(0, 58))
    assertEquals(0, ResumePolicy.resumePosition(-3, 58))
  }

  @Test
  fun `within last 5 seconds starts over (#659)`() {
    assertEquals(0, ResumePolicy.resumePosition(lastPosition = 56, timeRemaining = 2))
    assertEquals(0, ResumePolicy.resumePosition(lastPosition = 3595, timeRemaining = 5))
    assertEquals(50, ResumePolicy.resumePosition(lastPosition = 50, timeRemaining = 6))
  }

  @Test
  fun `past 95 percent starts over`() {
    // 2-hour film, 6 minutes left = 95%
    assertEquals(0, ResumePolicy.resumePosition(lastPosition = 6840, timeRemaining = 360))
    assertEquals(6800, ResumePolicy.resumePosition(lastPosition = 6800, timeRemaining = 400))
  }

  @Test
  fun `unknown duration keeps the position`() {
    assertEquals(120, ResumePolicy.resumePosition(lastPosition = 120, timeRemaining = 0))
  }

  @Test
  fun `restore seek is exact so it does not snap to a keyframe`() {
    assertArrayEquals(arrayOf("seek", "18", "absolute+exact"), ResumePolicy.seekCommand(18))
  }
}
