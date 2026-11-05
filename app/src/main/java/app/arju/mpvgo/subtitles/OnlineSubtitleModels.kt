package app.arju.mpvgo.subtitles

data class OnlineSubtitle(
  val id: String,
  val provider: String,
  val name: String,
  val language: String,
  val languageCode: String,
  val format: String = "srt",
  val isExactMatch: Boolean = false,
  val downloadCount: Int = 0,
  val rating: Float = 0f,
  val downloadUrl: String,
  val release: String? = null,
  val fileId: Int? = null,
)

data class SubtitleSearchQuery(
  val title: String,
  val movieHash: String? = null,
  val fileSize: Long? = null,
  val season: Int? = null,
  val episode: Int? = null,
  val year: String? = null,
  val languages: List<String> = emptyList(),
)
