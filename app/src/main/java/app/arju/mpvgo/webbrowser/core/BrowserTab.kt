package app.arju.mpvgo.webbrowser.core

import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One browser tab. Its WebView lives in RAM while the app runs; [TabManager] hibernates it
 * (saves state and destroys the WebView) only when Android reports low memory.
 */
class BrowserTab internal constructor(
  val id: Long,
  val isPrivate: Boolean,
  initialUrl: String?,
  scope: CoroutineScope,
  fetchText: suspend (url: String, referer: String, private: Boolean) -> String?,
) {
  internal val _url = MutableStateFlow(initialUrl.orEmpty())
  val url: StateFlow<String> = _url.asStateFlow()

  internal val _title = MutableStateFlow("")
  val title: StateFlow<String> = _title.asStateFlow()

  internal val _progress = MutableStateFlow(0)
  val progress: StateFlow<Int> = _progress.asStateFlow()

  internal val _loading = MutableStateFlow(false)
  val loading: StateFlow<Boolean> = _loading.asStateFlow()

  internal val _canGoBack = MutableStateFlow(false)
  val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

  internal val _canGoForward = MutableStateFlow(false)
  val canGoForward: StateFlow<Boolean> = _canGoForward.asStateFlow()

  internal val _blocked = MutableStateFlow(0)

  /** Ads and trackers blocked on the current page. */
  val blocked: StateFlow<Int> = _blocked.asStateFlow()

  internal val _favicon = MutableStateFlow<Bitmap?>(null)
  val favicon: StateFlow<Bitmap?> = _favicon.asStateFlow()

  internal val _hibernated = MutableStateFlow(false)
  val hibernated: StateFlow<Boolean> = _hibernated.asStateFlow()

  val sniffer = MediaSniffer(scope) { u, referer -> fetchText(u, referer, isPrivate) }

  /** Read from WebView network threads. */
  @Volatile internal var pageUrl: String = initialUrl.orEmpty()

  @Volatile internal var userAgent: String = ""

  internal var webView: WebView? = null
  internal var savedState: Bundle? = null
  internal var lastActive: Long = System.currentTimeMillis()

  /** Set when this tab was opened by a page (window.open / target=_blank). */
  internal var openerId: Long? = null

  val isStartPage: Boolean get() = _url.value.isEmpty()
}
