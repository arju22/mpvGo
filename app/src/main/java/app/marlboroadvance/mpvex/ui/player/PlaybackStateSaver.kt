package app.marlboroadvance.mpvex.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs playback-state (resume position) writes outside the Activity lifecycle so they are not
 * lost when the Activity stops/is destroyed or when the next save starts (#859, #852, #695).
 * Writes run one at a time in submission order and are not cancelled once submitted.
 */
class PlaybackStateSaver(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
  private val mutex = Mutex()

  fun saveAsync(block: suspend () -> Unit): Job =
    scope.launch { save(block) }

  suspend fun save(block: suspend () -> Unit) {
    withContext(NonCancellable) {
      mutex.withLock { block() }
    }
  }

  companion object {
    const val CHECKPOINT_INTERVAL_MS = 15_000L

    val shared: PlaybackStateSaver by lazy { PlaybackStateSaver() }
  }
}

/**
 * Calls [checkpoint] every [intervalMs] while [isPlaying] returns true, so the resume position
 * survives a process kill (e.g. swipe-away during background playback).
 */
fun CoroutineScope.launchPlaybackCheckpoints(
  intervalMs: Long = PlaybackStateSaver.CHECKPOINT_INTERVAL_MS,
  isPlaying: () -> Boolean,
  checkpoint: () -> Unit,
): Job =
  launch {
    while (isActive) {
      delay(intervalMs)
      if (isPlaying()) runCatching { checkpoint() }
    }
  }
