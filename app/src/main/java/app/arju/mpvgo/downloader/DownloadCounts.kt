package app.arju.mpvgo.downloader

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Live download counts shared with the Downloads tab badge. */
object DownloadCounts {
  private val _active = MutableStateFlow(0)
  val active: StateFlow<Int> = _active.asStateFlow()

  fun setActive(count: Int) {
    _active.value = count.coerceAtLeast(0)
  }

  private val _openRequested = MutableStateFlow(false)

  /** True when something (e.g. a download notification) asked for the Downloads tab. */
  val openRequested: StateFlow<Boolean> = _openRequested.asStateFlow()

  fun requestOpen() {
    _openRequested.value = true
  }

  fun consumeOpenRequest() {
    _openRequested.value = false
  }
}
