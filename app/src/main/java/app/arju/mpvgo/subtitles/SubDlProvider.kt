package app.arju.mpvgo.subtitles

import android.util.Log
import app.arju.mpvgo.BuildConfig
import app.arju.mpvgo.core.preferences.SubtitlesPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class SubDlProvider(
  private val client: OkHttpClient,
  private val prefs: SubtitlesPreferences,
) : SubtitleProvider {
  override val name: String = "SubDL"

  private val apiKey: String
    get() {
      val userKey = prefs.subDlApiKey.get().trim()
      return if (userKey.isNotBlank()) userKey else BuildConfig.SUBDL_API_KEY
    }

  override val isConfigured: Boolean
    get() = apiKey.isNotBlank()

  override suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitle> {
    val key = apiKey
    if (key.isBlank() || query.title.isBlank()) return emptyList()

    val urlBuilder = StringBuilder("https://api.subdl.com/api/v1/subtitles?")
    val params = mutableListOf<String>()
    params.add("api_key=${URLEncoder.encode(key, "UTF-8")}")
    params.add("film_name=${URLEncoder.encode(query.title, "UTF-8")}")

    if (query.season != null) {
      params.add("season_number=${query.season}")
      params.add("type=tv")
    } else {
      params.add("type=movie")
    }
    if (query.episode != null) {
      params.add("episode_number=${query.episode}")
    }
    if (query.languages.isNotEmpty()) {
      params.add("languages=${URLEncoder.encode(query.languages.joinToString(","), "UTF-8")}")
    }
    if (!query.year.isNullOrBlank()) {
      params.add("year=${URLEncoder.encode(query.year, "UTF-8")}")
    }

    urlBuilder.append(params.joinToString("&"))

    val request = Request.Builder()
      .url(urlBuilder.toString())
      .header("User-Agent", "mpvGo v1.1.0")
      .build()

    return runCatching {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) {
          Log.w("SubDL", "Search failed: HTTP ${resp.code}")
          return@use emptyList<OnlineSubtitle>()
        }
        val body = resp.body?.string() ?: return@use emptyList<OnlineSubtitle>()
        val json = JSONObject(body)
        if (!json.optBoolean("status", false)) return@use emptyList<OnlineSubtitle>()

        val subtitles = json.optJSONArray("subtitles") ?: return@use emptyList<OnlineSubtitle>()
        val list = mutableListOf<OnlineSubtitle>()

        for (i in 0 until subtitles.length()) {
          val item = subtitles.getJSONObject(i)
          val releaseName = item.optString("release_name", "subtitle")
          val lang = item.optString("lang", "en")
          val dlPath = item.optString("url", "")
          if (dlPath.isBlank()) continue

          val fullDlUrl = if (dlPath.startsWith("http")) dlPath else "https://dl.subdl.com$dlPath"

          list.add(
            OnlineSubtitle(
              id = "subdl_${i}_${releaseName.hashCode()}",
              provider = name,
              name = "$releaseName.srt",
              language = lang,
              languageCode = lang,
              format = "srt",
              isExactMatch = false,
              downloadCount = 0,
              rating = 0f,
              downloadUrl = fullDlUrl,
              release = releaseName,
            )
          )
        }
        list
      }
    }.getOrElse {
      Log.w("SubDL", "Exception searching: ${it.message}")
      emptyList()
    }
  }

  override suspend fun download(subtitle: OnlineSubtitle): Pair<String, String>? {
    val request = Request.Builder()
      .url(subtitle.downloadUrl)
      .header("User-Agent", "mpvGo v1.1.0")
      .build()

    return runCatching {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        val bytes = resp.body?.bytes() ?: return@use null
        SubtitleArchiveHelper.extractSubtitleText(bytes, subtitle.name)
      }
    }.getOrNull()
  }
}
