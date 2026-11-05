package app.arju.mpvgo.subtitles

interface SubtitleProvider {
  val name: String
  val isConfigured: Boolean
  suspend fun search(query: SubtitleSearchQuery): List<OnlineSubtitle>
  suspend fun download(subtitle: OnlineSubtitle): Pair<String, String>?
}
