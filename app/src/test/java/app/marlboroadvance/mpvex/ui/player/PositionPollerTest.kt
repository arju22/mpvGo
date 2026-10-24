package app.marlboroadvance.mpvex.ui.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PositionPollerTest {
  @Test
  fun `does not poll while inactive`() = runTest {
    val active = MutableStateFlow(false)
    var reads = 0
    val job = launchPositionPolling(active, 42L, { reads++; 1.0 }, {})
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(0, reads)
    job.cancel()
  }

  @Test
  fun `polls at interval while active and stops when inactive`() = runTest {
    val active = MutableStateFlow(true)
    var reads = 0
    val positions = mutableListOf<Float>()
    val job = launchPositionPolling(active, 42L, { reads++; reads.toDouble() }, { positions += it })
    runCurrent()
    assertEquals(1, reads)
    advanceTimeBy(420)
    runCurrent()
    assertEquals(11, reads)
    assertEquals(11f, positions.last())

    active.value = false
    runCurrent()
    val readsWhenStopped = reads
    advanceTimeBy(10_000)
    runCurrent()
    assertEquals(readsWhenStopped, reads)

    active.value = true
    runCurrent()
    assertEquals(readsWhenStopped + 1, reads)
    job.cancel()
  }

  @Test
  fun `null reads are skipped`() = runTest {
    val active = MutableStateFlow(true)
    val positions = mutableListOf<Float>()
    val job = launchPositionPolling(active, 42L, { null }, { positions += it })
    advanceTimeBy(500)
    runCurrent()
    assertEquals(0, positions.size)
    job.cancel()
  }
}
