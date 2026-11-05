package app.arju.mpvgo.subtitles

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

class PodnapisiProvider(
  private val client: OkHttpClient,
) : SubtitleProvider {
  override val name: String = "Podnapisi"
  override val isConfigured: Boolean = true // Keyless open provider

  override suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitle> {
    if (query.title.isBlank()) return emptyList()

    val encodedQuery = URLEncoder.encode(query.title, "UTF-8")
    val urlBuilder = StringBuilder("https://www.podnapisi.net/subtitles/search/old?sXML=1&sK=$encodedQuery")

    if (query.season != null) {
      urlBuilder.append("&sTS=${query.season}")
    }
    if (query.episode != null) {
      urlBuilder.append("&sTE=${query.episode}")
    }
    if (!query.year.isNullOrBlank()) {
      urlBuilder.append("&sY=${query.year}")
    }

    val request = Request.Builder()
      .url(urlBuilder.toString())
      .header("User-Agent", "mpvGo v1.1.0")
      .build()

    return runCatching {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return@use emptyList<OnlineSubtitle>()
        val xml = resp.body?.string() ?: return@use emptyList<OnlineSubtitle>()
        parseXml(xml)
      }
    }.getOrElse {
      Log.w("Podnapisi", "Exception searching: ${it.message}")
      emptyList()
    }
  }

  private fun parseXml(xml: String): List<OnlineSubtitle> {
    val results = mutableListOf<OnlineSubtitle>()
    runCatching {
      val factory = DocumentBuilderFactory.newInstance()
      val builder = factory.newDocumentBuilder()
      val doc = builder.parse(org.xml.sax.InputSource(StringReader(xml)))
      val nodes = doc.getElementsByTagName("subtitle")

      for (i in 0 until nodes.length) {
        val elem = nodes.item(i) as? org.w3c.dom.Element ?: continue
        val pid = elem.getElementsByTagName("pid").item(0)?.textContent.orEmpty()
        val title = elem.getElementsByTagName("title").item(0)?.textContent.orEmpty()
        val lang = elem.getElementsByTagName("language").item(0)?.textContent.orEmpty()
        val release = elem.getElementsByTagName("release").item(0)?.textContent.orEmpty()
        val downloads = elem.getElementsByTagName("downloads").item(0)?.textContent?.toIntOrNull() ?: 0
        val rating = elem.getElementsByTagName("rating").item(0)?.textContent?.toFloatOrNull() ?: 0f

        if (pid.isBlank()) continue

        val fileName = if (release.isNotBlank()) "$release.srt" else "$title.srt"
        results.add(
          OnlineSubtitle(
            id = "pod_$pid",
            provider = name,
            name = fileName,
            language = lang,
            languageCode = lang,
            format = "srt",
            isExactMatch = false,
            downloadCount = downloads,
            rating = rating,
            downloadUrl = "https://www.podnapisi.net/subtitles/$pid/download",
            release = release.ifBlank { null },
          )
        )
      }
    }
    return results
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
