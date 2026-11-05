package app.arju.mpvgo.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/**
 * Everything the player needs to open a web stream the way the page opened it.
 * Travels as intent extras so external apps that only send "title"/"headers" keep working.
 */
data class MediaRequest(
  val url: String,
  val title: String? = null,
  val userAgent: String? = null,
  val referer: String? = null,
  val origin: String? = null,
  val cookies: String? = null,
  val pageUrl: String? = null,
  val extraHeaders: Map<String, String> = emptyMap(),
  /** From a private tab: not added to Recents or history. */
  val isPrivate: Boolean = false,
  /** "Add web videos to Recents" is off. */
  val skipRecents: Boolean = false,
  /** Open in Android Picture-in-Picture mode when launched from the browser. */
  val startInPip: Boolean = false,
) {
  fun toIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse(url), context, PlayerActivity::class.java).apply {
      putExtras(toExtras())
    }

  fun toExtras(): Bundle = Bundle().apply {
    title?.takeIf { it.isNotBlank() }?.let { putString(EXTRA_TITLE, it) }
    val pairs = HttpHeaderPlan.toPairs(this@MediaRequest)
    if (pairs.isNotEmpty()) putStringArray(EXTRA_HEADERS, pairs.toTypedArray())
    pageUrl?.let { putString(EXTRA_PAGE_URL, it) }
    if (isPrivate) putBoolean(EXTRA_PRIVATE, true)
    if (skipRecents) putBoolean(EXTRA_NO_RECENTS, true)
    if (startInPip) putBoolean(EXTRA_START_IN_PIP, true)
  }

  companion object {
    const val EXTRA_TITLE = "title"
    const val EXTRA_HEADERS = "headers"
    const val EXTRA_PAGE_URL = "app.arju.mpvgo.extra.PAGE_URL"
    const val EXTRA_PRIVATE = "app.arju.mpvgo.extra.PRIVATE"
    const val EXTRA_NO_RECENTS = "app.arju.mpvgo.extra.NO_RECENTS"
    const val EXTRA_START_IN_PIP = "app.arju.mpvgo.extra.START_IN_PIP"
  }
}

/** The user agent and header lines to hand mpv before `loadfile`. */
data class HttpHeaderPlan(val userAgent: String?, val fields: List<String>) {
  companion object {
    /** Flat key/value list in the mpv-android "headers" extra format. */
    fun toPairs(request: MediaRequest): List<String> = buildList {
      request.userAgent?.takeIf { it.isNotBlank() }?.let { add("User-Agent"); add(it) }
      request.referer?.takeIf { it.isNotBlank() }?.let { add("Referer"); add(it) }
      request.origin?.takeIf { it.isNotBlank() }?.let { add("Origin"); add(it) }
      request.cookies?.takeIf { it.isNotBlank() }?.let { add("Cookie"); add(it) }
      request.extraHeaders.forEach { (k, v) -> add(k); add(v) }
    }

    /**
     * Builds the plan from the "headers" extra (key, value, key, value...).
     * [autoReferer] is used only when no Referer was given; [extraCookies] (e.g. from the
     * WebView cookie store) are merged into the Cookie header.
     */
    fun build(headers: Array<String>?, autoReferer: String?, extraCookies: String? = null): HttpHeaderPlan {
      var userAgent: String? = null
      val map = LinkedHashMap<String, Pair<String, String>>()
      autoReferer?.takeIf { it.isNotBlank() }?.let { map["referer"] = "Referer" to it }
      headers?.toList()?.chunked(2)?.forEach { chunk ->
        if (chunk.size < 2) return@forEach
        val key = chunk[0].trim()
        val value = chunk[1].trim()
        if (key.isEmpty() || value.isEmpty() || key.any { it == '\r' || it == '\n' } || value.any { it == '\r' || it == '\n' }) return@forEach
        if (key.equals("User-Agent", ignoreCase = true)) userAgent = value else map[key.lowercase()] = key to value
      }
      val cookie = mergeCookies(map["cookie"]?.second, extraCookies)
      if (cookie != null) map["cookie"] = (map["cookie"]?.first ?: "Cookie") to cookie
      return HttpHeaderPlan(userAgent, map.values.map { (k, v) -> "$k: $v" })
    }

    /** mpv string-list syntax: items joined by ','; mpv only unescapes '\,' (other backslashes stay literal). */
    fun toMpvList(items: List<String>): String =
      items.joinToString(",") { it.replace(",", "\\,") }

    /** Merges `a=1; b=2` style cookie strings; earlier values win for duplicate names. */
    fun mergeCookies(vararg sources: String?): String? {
      val merged = LinkedHashMap<String, String>()
      sources.filterNotNull().forEach { source ->
        source.split(';').forEach { part ->
          val trimmed = part.trim()
          if (trimmed.isEmpty()) return@forEach
          val name = trimmed.substringBefore('=').trim()
          if (name.isNotEmpty() && name !in merged) merged[name] = trimmed
        }
      }
      return merged.values.takeIf { it.isNotEmpty() }?.joinToString("; ")
    }
  }
}
