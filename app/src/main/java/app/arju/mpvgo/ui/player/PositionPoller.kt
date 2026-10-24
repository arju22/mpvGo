package app.arju.mpvgo.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Polls the playback position only while [active] is true (e.g. while the seekbar is visible),
 * so no wake-ups happen while the controls are hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun CoroutineScope.launchPositionPolling(
  active: Flow<Boolean>,
  intervalMs: Long = 42L,
  read: () -> Double?,
  onPosition: (Float) -> Unit,
): Job = launch {
  active.distinctUntilChanged().collectLatest { isActive ->
    if (!isActive) return@collectLatest
    while (true) {
      read()?.let { onPosition(it.toFloat()) }
      delay(intervalMs)
    }
  }
}
