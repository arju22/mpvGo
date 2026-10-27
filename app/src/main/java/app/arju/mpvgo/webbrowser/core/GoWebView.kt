package app.arju.mpvgo.webbrowser.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ProfileStore
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.arju.mpvgo.webbrowser.adblock.AdBlockPreferences
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import app.arju.mpvgo.webbrowser.adblock.RequestTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

/** What a [BrowserTab]'s WebView needs from its owner. */
internal interface TabHost {
  fun onPlay(tab: BrowserTab, src: String?, title: String, drm: Boolean, reply: JavaScriptReplyProxy)

  fun onCreateWindow(tab: BrowserTab, isUserGesture: Boolean, resultMsg: Message): Boolean

  fun onCloseWindow(tab: BrowserTab)

  fun onRendererGone(tab: BrowserTab)

  /** A link to another app (intent:, market:, tel: ...). Return true when handled. */
  fun onExternalUrl(tab: BrowserTab, url: String, hasGesture: Boolean): Boolean

  fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback)

  fun onHideCustomView()

  /** A page was committed to the tab's history (also fires for in-page navigation). */
  fun onVisited(tab: BrowserTab, url: String)

  fun onTitle(tab: BrowserTab, title: String)

  fun onPageFinished(tab: BrowserTab, view: WebView)

  /** The page asked to download a file (Content-Disposition: attachment, unknown types ...). */
  fun onDownloadStart(tab: BrowserTab, url: String, contentDisposition: String?, mimeType: String?)

  /** The user long-pressed a link. */
  fun onLinkLongPress(tab: BrowserTab, url: String)
}

internal class GoWebViewFactory(
  private val appContext: Context,
  private val adBlocker: AdBlocker,
  private val adPrefs: AdBlockPreferences,
  private val prefs: BrowserPreferences,
  private val scope: CoroutineScope,
) {
  private val hookScript: String by lazy {
    appContext.assets.open("webbrowser/mpvgo_hook.js").bufferedReader().use { it.readText() }
  }

  private val emptyResponse: WebResourceResponse
    get() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

  private var serviceWorkersHooked = false

  /** Service workers fetch outside any WebView client; block ads there too. */
  @SuppressLint("RequiresFeature")
  fun hookServiceWorkers(privateProfile: Boolean) {
    runCatching {
      if (!privateProfile && !serviceWorkersHooked &&
        WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)
      ) {
        serviceWorkersHooked = true
        ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(
          object : ServiceWorkerClientCompat() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? = swIntercept(request)
          },
        )
      }
      if (privateProfile && BrowserProfiles.privateSupported) {
        ProfileStore.getInstance().getOrCreateProfile(BrowserProfiles.PRIVATE).serviceWorkerController.setServiceWorkerClient(
          object : android.webkit.ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? = swIntercept(request)
          },
        )
      }
    }
  }

  private fun swIntercept(request: WebResourceRequest): WebResourceResponse? {
    val url = request.url.toString()
    if (!url.startsWith("http")) return null
    val type = RequestTypes.of(url, request.requestHeaders["Accept"], isMainFrame = false)
    // No page context here; the request's own origin stands in for it.
    val source = originOf(url) ?: url
    return if (adBlocker.shouldBlock(url, source, type)) emptyResponse else null
  }

  @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
  fun create(
    context: Context,
    tab: BrowserTab,
    host: TabHost,
  ): WebView {
    val webView = WebView(context)
    BrowserProfiles.apply(webView, tab.isPrivate)
    hookServiceWorkers(tab.isPrivate)
    webView.settings.apply {
      domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = true
      loadWithOverviewMode = true
      useWideViewPort = true
      builtInZoomControls = true
      displayZoomControls = false
      setSupportMultipleWindows(true)
      javaScriptCanOpenWindowsAutomatically = false
      mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
      allowFileAccess = false
      allowContentAccess = false
      if (tab.isPrivate) cacheMode = WebSettings.LOAD_NO_CACHE
    }
    tab.mobileUserAgent = ""
    applySettings(webView, tab)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
      WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("*")) { view, message, _, isMainFrame, reply ->
        onMessage(view, tab, host, message, isMainFrame, reply)
      }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
      WebViewCompat.addDocumentStartJavaScript(webView, hookScript, setOf("*"))
      if (prefs.doNotTrack.get()) WebViewCompat.addDocumentStartJavaScript(webView, PrivacyHeaders.SCRIPT, setOf("*"))
    }
    webView.webViewClient = Client(tab, host)
    webView.webChromeClient = Chrome(tab, host)
    webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
      host.onDownloadStart(tab, url, contentDisposition, mimeType)
    }
    webView.setOnLongClickListener { onLongPress(it as WebView, tab, host) }
    return webView
  }

  private fun onLongPress(
    view: WebView,
    tab: BrowserTab,
    host: TabHost,
  ): Boolean {
    val hit = view.hitTestResult
    return when (hit.type) {
      WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
        val url = hit.extra?.takeIf(::isWebLink) ?: return false
        host.onLinkLongPress(tab, url)
        true
      }
      WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
        val handler =
          android.os.Handler(android.os.Looper.getMainLooper()) { msg ->
            msg.data.getString("url")?.takeIf(::isWebLink)?.let { host.onLinkLongPress(tab, it) }
            true
          }
        view.requestFocusNodeHref(handler.obtainMessage())
        true
      }
      else -> false
    }
  }

  private fun isWebLink(url: String) = url.startsWith("http://", true) || url.startsWith("https://", true)

  /** Applies the browser settings that can change while a tab is open. */
  @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
  fun applySettings(
    webView: WebView,
    tab: BrowserTab,
  ) {
    val settings = webView.settings
    settings.javaScriptEnabled = prefs.javascript.get()
    settings.textZoom = prefs.textZoom.get().coerceIn(50, 300)
    settings.safeBrowsingEnabled = prefs.safeBrowsing.get()
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
      WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, prefs.darkWebsites.get())
    }
    runCatching { BrowserProfiles.cookieManager(tab.isPrivate).setAcceptThirdPartyCookies(webView, !prefs.blockThirdPartyCookies.get()) }
    val mobile = prefs.userAgent.get().ifBlank { defaultUserAgent }
    if (mobile != tab.mobileUserAgent) {
      tab.mobileUserAgent = mobile
      settings.userAgentString = if (tab.desktop.value) DesktopMode.userAgent(mobile) else mobile
      tab.userAgent = settings.userAgentString
    }
  }

  private val defaultUserAgent: String by lazy { WebSettings.getDefaultUserAgent(appContext) }

  /** Hosts whose https version failed this session; they load over http. */
  private val httpOnlyHosts: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

  /** Hosts this class switched to https, so a failure can fall back. */
  private val upgradedHosts: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

  /** Loads [url] with the HTTPS upgrade and privacy headers applied. */
  fun load(
    view: WebView,
    url: String,
  ) {
    view.loadUrl(upgrade(url) ?: url, PrivacyHeaders.of(prefs.doNotTrack.get()))
  }

  private fun upgrade(url: String): String? {
    if (!prefs.httpsUpgrade.get()) return null
    val secure = HttpsUpgrade.upgrade(url, httpOnlyHosts) ?: return null
    HttpsUpgrade.hostOf(url)?.let(upgradedHosts::add)
    return secure
  }

  /** The upgraded https page failed: remember the host and load it over http. */
  private fun fallBack(
    view: WebView,
    url: String,
  ): Boolean {
    if (!url.startsWith("https://")) return false
    val host = HttpsUpgrade.hostOf(url) ?: return false
    if (host !in upgradedHosts || !httpOnlyHosts.add(host)) return false
    Log.d(TAG, "https failed for $host, falling back to http")
    view.post { view.loadUrl(HttpsUpgrade.downgrade(url), PrivacyHeaders.of(prefs.doNotTrack.get())) }
    return true
  }

  private fun onMessage(
    view: WebView,
    tab: BrowserTab,
    host: TabHost,
    message: WebMessageCompat,
    isMainFrame: Boolean,
    reply: JavaScriptReplyProxy,
  ) {
    val json = runCatching { JSONObject(message.data ?: return) }.getOrNull() ?: return
    val frame = json.optString("frame")
    when (json.optString("t")) {
      "hello" -> {
        if (!adPrefs.cosmetic.get() || !adBlocker.isEnabled) return
        scope.launch(Dispatchers.Default) {
          val cosmetic = adBlocker.cosmetic(frame)
          Log.d(TAG, "hello $frame cosmetic=${cosmetic?.hideSelectors?.length} generic=${cosmetic?.genericHide}")
          cosmetic ?: return@launch
          val out =
            JSONObject()
              .put("cmd", "cosmetic")
              .put("genericHide", cosmetic.genericHide)
              .put("css", cosmetic.hideSelectors)
              .put("exceptions", cosmetic.exceptions)
              .put("script", cosmetic.injectedScript)
          withContext(Dispatchers.Main) { runCatching { reply.postMessage(out.toString()) } }
        }
      }
      "classids" -> {
        val classes = json.optJSONArray("classes").strings()
        val ids = json.optJSONArray("ids").strings()
        val exceptions = json.optString("exceptions")
        scope.launch(Dispatchers.Default) {
          val css = adBlocker.classIdSelectors(classes, ids, exceptions)
          Log.d(TAG, "classids ${classes.size}/${ids.size} -> ${css.length}")
          if (css.isEmpty()) return@launch
          val out = JSONObject().put("cmd", "css").put("css", css)
          withContext(Dispatchers.Main) { runCatching { reply.postMessage(out.toString()) } }
        }
      }
      "media" ->
        tab.sniffer.onRequest(
          url = json.optString("url"),
          mime = json.optString("mime").ifBlank { null },
          frameUrl = frame.ifBlank { tab.pageUrl },
          pageUrl = tab.pageUrl,
          pageTitle = tab._title.value,
          size = json.optLong("len", -1),
        )
      "drm" -> tab.sniffer.markDrm()
      "play" -> {
        if (!json.optBoolean("video", true)) return
        val src = json.optString("src").ifBlank { null }
        if (src != null && src.startsWith("http")) {
          tab.sniffer.onRequest(src, null, frame.ifBlank { tab.pageUrl }, tab.pageUrl, tab._title.value)
        }
        host.onPlay(tab, src, json.optString("title").ifBlank { tab._title.value }, json.optBoolean("drm"), reply)
      }
    }
  }

  private inner class Client(
    private val tab: BrowserTab,
    private val host: TabHost,
  ) : WebViewClient() {
    override fun shouldInterceptRequest(
      view: WebView,
      request: WebResourceRequest,
    ): WebResourceResponse? {
      if (request.isForMainFrame) return null
      val url = request.url.toString()
      if (!url.startsWith("http")) return null
      val headers = request.requestHeaders
      val page = tab.pageUrl
      val type =
        RequestTypes.of(
          url,
          headers["Accept"] ?: headers["accept"],
          isMainFrame = false,
          isXhr = headers["X-Requested-With"] == "XMLHttpRequest",
        )
      if (adBlocker.shouldBlock(url, page, type, page)) {
        tab._blocked.value += 1
        return emptyResponse
      }
      val referer = headers["Referer"] ?: headers["referer"] ?: page
      tab.sniffer.onRequest(url, null, referer, page, tab._title.value)
      return null
    }

    override fun shouldOverrideUrlLoading(
      view: WebView,
      request: WebResourceRequest,
    ): Boolean {
      val url = request.url.toString()
      val scheme = request.url.scheme?.lowercase()
      if (scheme == "http" || scheme == "https") {
        // Ad redirects: the lists' document rules catch popunders and forced redirects.
        if (request.isForMainFrame && adPrefs.blockPopups.get() && !request.hasGesture() &&
          adBlocker.shouldBlock(url, tab.pageUrl, "document", tab.pageUrl)
        ) {
          tab._blocked.value += 1
          return true
        }
        if (request.isForMainFrame && !request.isRedirect && scheme == "http") {
          upgrade(url)?.let { secure ->
            view.loadUrl(secure, PrivacyHeaders.of(prefs.doNotTrack.get()))
            return true
          }
        }
        return false
      }
      if (scheme in INTERNAL_SCHEMES) return false
      return host.onExternalUrl(tab, url, request.hasGesture()) || true
    }

    override fun onPageStarted(
      view: WebView,
      url: String,
      favicon: Bitmap?,
    ) {
      if (url != tab.pageUrl) {
        tab.sniffer.reset()
        tab._blocked.value = 0
      }
      tab.pageUrl = url
      tab._url.value = url
      tab._loading.value = true
      tab._favicon.value = favicon
      updateNav(view)
    }

    override fun onPageFinished(
      view: WebView,
      url: String,
    ) {
      tab._loading.value = false
      tab._progress.value = 100
      view.title?.takeIf { it.isNotBlank() }?.let { tab._title.value = it }
      updateNav(view)
      host.onPageFinished(tab, view)
    }

    override fun doUpdateVisitedHistory(
      view: WebView,
      url: String,
      isReload: Boolean,
    ) {
      tab.pageUrl = url
      tab._url.value = url
      updateNav(view)
      if (!isReload) host.onVisited(tab, url)
    }

    override fun onRenderProcessGone(
      view: WebView,
      detail: RenderProcessGoneDetail,
    ): Boolean {
      host.onRendererGone(tab)
      return true
    }

    override fun onReceivedError(
      view: WebView,
      request: WebResourceRequest,
      error: android.webkit.WebResourceError,
    ) {
      if (request.isForMainFrame) fallBack(view, request.url.toString())
    }

    override fun onReceivedSslError(
      view: WebView,
      handler: android.webkit.SslErrorHandler,
      error: android.net.http.SslError,
    ) {
      handler.cancel()
      fallBack(view, error.url)
    }

    private fun updateNav(view: WebView) {
      tab._canGoBack.value = view.canGoBack()
      tab._canGoForward.value = view.canGoForward()
    }
  }

  private inner class Chrome(
    private val tab: BrowserTab,
    private val host: TabHost,
  ) : WebChromeClient() {
    override fun onProgressChanged(
      view: WebView,
      newProgress: Int,
    ) {
      tab._progress.value = newProgress
    }

    override fun onReceivedTitle(
      view: WebView,
      title: String?,
    ) {
      if (!title.isNullOrBlank()) {
        tab._title.value = title
        host.onTitle(tab, title)
      }
    }

    override fun onReceivedIcon(
      view: WebView,
      icon: Bitmap?,
    ) {
      tab._favicon.value = icon
    }

    override fun onCreateWindow(
      view: WebView,
      isDialog: Boolean,
      isUserGesture: Boolean,
      resultMsg: Message,
    ): Boolean {
      if (!isUserGesture && adPrefs.blockPopups.get()) {
        tab._blocked.value += 1
        return false
      }
      return host.onCreateWindow(tab, isUserGesture, resultMsg)
    }

    override fun onCloseWindow(window: WebView) {
      host.onCloseWindow(tab)
    }

    override fun onShowCustomView(
      view: View,
      callback: CustomViewCallback,
    ) {
      host.onShowCustomView(view, callback)
    }

    override fun onHideCustomView() {
      host.onHideCustomView()
    }

    override fun onPermissionRequest(request: PermissionRequest) {
      request.deny()
    }

    override fun onGeolocationPermissionsShowPrompt(
      origin: String?,
      callback: GeolocationPermissions.Callback,
    ) {
      callback.invoke(origin, false, false)
    }
  }

  companion object {
    const val BRIDGE = "mpvgoBridge"
    private const val TAG = "GoWebView"
    private val INTERNAL_SCHEMES = setOf("about", "data", "blob", "javascript")

    private fun JSONArray?.strings(): List<String> {
      if (this == null) return emptyList()
      return (0 until length()).mapNotNull { optString(it).takeIf(String::isNotEmpty) }
    }

    fun originOf(url: String): String? =
      runCatching {
        val uri = Uri.parse(url)
        val port = if (uri.port > 0) ":${uri.port}" else ""
        uri.scheme?.let { s -> uri.host?.let { h -> "$s://$h$port" } }
      }.getOrNull()
  }
}
