package app.arju.mpvgo.webbrowser.core

import android.webkit.CookieManager
import android.webkit.WebView
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Private tabs run in their own WebView profile ("mpvgo_private"), so cookies and storage are
 * isolated from normal tabs, and wiping them never logs the user out of normal tabs.
 */
object BrowserProfiles {
  const val PRIVATE = "mpvgo_private"

  val privateSupported: Boolean by lazy {
    runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)
  }

  /** Must run before the WebView loads anything. */
  fun apply(
    webView: WebView,
    private: Boolean,
  ) {
    if (private && privateSupported) {
      ProfileStore.getInstance().getOrCreateProfile(PRIVATE)
      WebViewCompat.setProfile(webView, PRIVATE)
    }
  }

  fun cookieManager(private: Boolean): CookieManager =
    if (private && privateSupported) {
      ProfileStore.getInstance().getOrCreateProfile(PRIVATE).cookieManager
    } else {
      CookieManager.getInstance()
    }

  fun cookies(
    private: Boolean,
    vararg urls: String?,
  ): String? =
    runCatching {
      val manager = cookieManager(private)
      app.arju.mpvgo.player.HttpHeaderPlan.mergeCookies(
        *urls.filterNotNull().map { manager.getCookie(it) }.toTypedArray(),
      )
    }.getOrNull()

  /** Wipes everything the private profile stored. Call after the last private tab is destroyed. */
  fun clearPrivate() {
    if (!privateSupported) return
    runCatching {
      val store = ProfileStore.getInstance()
      if (!store.deleteProfile(PRIVATE)) {
        store.getProfile(PRIVATE)?.let {
          it.cookieManager.removeAllCookies(null)
          it.webStorage.deleteAllData()
        }
      }
    }
  }
}
