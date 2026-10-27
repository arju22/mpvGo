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
      javaScriptEnabled = prefs.javascript.get()
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
      prefs.userAgent.get().takeIf { it.isNotBlank() }?.let { userAgentString = it }
      if (tab.isPrivate) cacheMode = WebSettings.LOAD_NO_CACHE
    }
    tab.userAgent = webView.settings.userAgentString
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
      WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("*")) { view, message, _, isMainFrame, reply ->
        onMessage(view, tab, host, message, isMainFrame, reply)
      }
    }
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
      WebViewCompat.addDocumentStartJavaScript(webView, hookScript, setOf("*"))
    }
    webView.webViewClient = Client(tab, host)
    webView.webChromeClient = Chrome(tab, host)
    return webView
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
    }

    override fun doUpdateVisitedHistory(
      view: WebView,
      url: String,
      isReload: Boolean,
    ) {
      tab.pageUrl = url
      tab._url.value = url
      updateNav(view)
    }

    override fun onRenderProcessGone(
      view: WebView,
      detail: RenderProcessGoneDetail,
    ): Boolean {
      host.onRendererGone(tab)
      return true
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
      if (!title.isNullOrBlank()) tab._title.value = title
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
