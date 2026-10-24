package app.marlboroadvance.mpvex.ui.player

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackStateSaverTest {

  @Test
  fun `queued saves all complete in submission order`() = runTest {
    val saver = PlaybackStateSaver(TestScope(testScheduler))
    val written = mutableListOf<String>()
    saver.saveAsync { delay(100); written += "videoA" }
    saver.saveAsync { written += "videoB" }
    saver.saveAsync { delay(10); written += "videoC" }
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(listOf("videoA", "videoB", "videoC"), written)
  }

  @Test
  fun `cancelling a started save does not drop the write`() = runTest {
    val saver = PlaybackStateSaver(TestScope(testScheduler))
    val started = CompletableDeferred<Unit>()
    var written = false
    val job = saver.saveAsync {
      started.complete(Unit)
      delay(500)
      written = true
    }
    runCurrent()
    started.await()
    job.cancelAndJoin()
    advanceTimeBy(1_000)
    runCurrent()
    assertEquals(true, written)
  }

  @Test
  fun `sync save waits for earlier queued saves`() = runTest {
    val saver = PlaybackStateSaver(TestScope(testScheduler))
    val written = mutableListOf<String>()
    saver.saveAsync { delay(200); written += "checkpoint" }
    runCurrent()
    saver.save { written += "final" }
    assertEquals(listOf("checkpoint", "final"), written)
  }

  @Test
  fun `queued save writes under the key captured at submission (onNewIntent)`() = runTest {
    val saver = PlaybackStateSaver(TestScope(testScheduler))
    val db = mutableMapOf<String, Int>()
    var currentKey = "base.mp4_1"
    var currentPos = 18

    fun save() {
      val key = currentKey
      val pos = currentPos
      saver.saveAsync { delay(50); db[key] = pos }
    }

    saver.saveAsync { delay(500) } // a slow earlier write keeps the next one queued
    save() // onPause for base.mp4
    currentKey = "same.mp4_2" // onNewIntent switches files before the queued write runs
    currentPos = 2
    advanceTimeBy(1_000)
    runCurrent()

    assertEquals(mapOf("base.mp4_1" to 18), db)
  }

  @Test
  fun `checkpoints run only while playing`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    var playing = true
    var count = 0
    val job = scope.launchPlaybackCheckpoints(
      intervalMs = 15_000,
      isPlaying = { playing },
      checkpoint = { count++ },
    )
    advanceTimeBy(45_001)
    assertEquals(3, count)
    playing = false
    advanceTimeBy(60_000)
    assertEquals(3, count)
    playing = true
    advanceTimeBy(15_000)
    assertEquals(4, count)
    job.cancel()
    advanceTimeBy(60_000)
    assertEquals(4, count)
  }

  @Test
  fun `a failing checkpoint does not stop later checkpoints`() = runTest {
    val scope = TestScope(StandardTestDispatcher(testScheduler))
    var count = 0
    val job = scope.launchPlaybackCheckpoints(
      intervalMs = 1_000,
      isPlaying = { true },
      checkpoint = { count++; if (count == 1) error("boom") },
    )
    advanceTimeBy(3_001)
    assertEquals(3, count)
    job.cancel()
  }
}
