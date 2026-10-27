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
}
