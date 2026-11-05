package app.arju.mpvgo.subtitles

import android.util.Log
import app.arju.mpvgo.BuildConfig
import app.arju.mpvgo.core.preferences.SubtitlesPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

class OpenSubtitlesProvider(
  private val client: OkHttpClient,
  private val prefs: SubtitlesPreferences,
) : SubtitleProvider {
  override val name: String = "OpenSubtitles"

  private val apiKey: String
    get() {
      val userKey = prefs.openSubtitlesApiKey.get().trim()
      return if (userKey.isNotBlank()) userKey else BuildConfig.OPENSUBTITLES_API_KEY
    }

  override val isConfigured: Boolean
    get() = apiKey.isNotBlank()

  override suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitle> {
    val key = apiKey
    if (key.isBlank()) return emptyList()

    val urlBuilder = StringBuilder("https://api.opensubtitles.com/api/v1/subtitles?")
    val params = mutableListOf<String>()

    if (!query.movieHash.isNullOrBlank()) {
      params.add("moviehash=${URLEncoder.encode(query.movieHash, "UTF-8")}")
    }
    if (query.title.isNotBlank()) {
      params.add("query=${URLEncoder.encode(query.title, "UTF-8")}")
    }
    if (query.languages.isNotEmpty()) {
      params.add("languages=${URLEncoder.encode(query.languages.joinToString(","), "UTF-8")}")
    }
    if (query.season != null) {
      params.add("season_number=${query.season}")
    }
    if (query.episode != null) {
      params.add("episode_number=${query.episode}")
    }
    if (!query.year.isNullOrBlank()) {
      params.add("year=${URLEncoder.encode(query.year, "UTF-8")}")
    }

    urlBuilder.append(params.joinToString("&"))

    val request = Request.Builder()
      .url(urlBuilder.toString())
      .header("Api-Key", key)
      .header("User-Agent", "mpvGo v1.1.0")
      .build()

    return runCatching {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) {
          Log.w("OpenSubtitles", "Search failed: HTTP ${resp.code}")
          return@use emptyList<OnlineSubtitle>()
        }
        val body = resp.body?.string() ?: return@use emptyList<OnlineSubtitle>()
        val json = JSONObject(body)
        val data = json.optJSONArray("data") ?: return@use emptyList<OnlineSubtitle>()
        val list = mutableListOf<OnlineSubtitle>()

        for (i in 0 until data.length()) {
          val item = data.getJSONObject(i)
          val id = item.optString("id", "")
          val attrs = item.optJSONObject("attributes") ?: continue
          val lang = attrs.optString("language", "en")
          val release = attrs.optString("release", "")
          val moviehashMatch = attrs.optBoolean("moviehash_match", false)
          val ratings = attrs.optDouble("ratings", 0.0).toFloat()
          val downloadCount = attrs.optInt("download_count", 0)

          val files = attrs.optJSONArray("files")
          val fileObj = if (files != null && files.length() > 0) files.getJSONObject(0) else null
          val fileId = fileObj?.optInt("file_id", -1)?.takeIf { it > 0 }
          val fileName = fileObj?.optString("file_name", release.ifBlank { "subtitle.srt" }) ?: "subtitle.srt"

          list.add(
            OnlineSubtitle(
              id = "os_$id",
              provider = name,
              name = fileName,
              language = lang,
              languageCode = lang,
              format = fileName.substringAfterLast('.', "srt"),
              isExactMatch = moviehashMatch,
              downloadCount = downloadCount,
              rating = ratings,
              downloadUrl = "https://api.opensubtitles.com/api/v1/download",
              release = release.ifBlank { null },
              fileId = fileId,
            )
          )
        }
        list
      }
    }.getOrElse {
      Log.w("OpenSubtitles", "Exception searching: ${it.message}")
      emptyList()
    }
  }

  override suspend fun download(subtitle: OnlineSubtitle): Pair<String, String>? {
    val key = apiKey
    val fileId = subtitle.fileId ?: return null
    if (key.isBlank()) return null

    val jsonBody = JSONObject().put("file_id", fileId).toString()
    val postReq = Request.Builder()
      .url("https://api.opensubtitles.com/api/v1/download")
      .header("Api-Key", key)
      .header("User-Agent", "mpvGo v1.1.0")
      .post(jsonBody.toRequestBody("application/json".toMediaType()))
      .build()

    return runCatching {
      val link = client.newCall(postReq).execute().use { resp ->
        if (!resp.isSuccessful) return@use null
        val body = resp.body?.string() ?: return@use null
        JSONObject(body).optString("link", "").takeIf { it.isNotBlank() }
      } ?: return@runCatching null

      val fileReq = Request.Builder()
        .url(link)
        .header("User-Agent", "mpvGo v1.1.0")
        .build()

      client.newCall(fileReq).execute().use { fileResp ->
        if (!fileResp.isSuccessful) return@use null
        val bytes = fileResp.body?.bytes() ?: return@use null
        SubtitleArchiveHelper.extractSubtitleText(bytes, subtitle.name)
      }
    }.getOrNull()
  }
}
