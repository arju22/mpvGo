package app.arju.mpvgo.webbrowser.core

/** Desktop-site mode: a desktop Linux Chrome user agent derived from the WebView's mobile one. */
object DesktopMode {
  private val PLATFORM = Regex("\\(Linux; Android[^)]*\\)")

  fun userAgent(mobile: String): String =
    mobile
      .replace(PLATFORM, "(X11; Linux x86_64)")
      .replace(" Mobile Safari/", " Safari/")
      .replace(Regex(" Version/[\\d.]+"), "")
      .replace("; wv)", ")")
}
