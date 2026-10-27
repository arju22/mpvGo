package app.arju.mpvgo.webbrowser.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Normal tabs saved for "Remember tabs". Private tabs are never written here. */
@Serializable
data class SavedTab(
  val url: String,
  val title: String = "",
  val desktop: Boolean = false,
  val scrollY: Int = 0,
)

@Serializable
data class SavedSession(
  val tabs: List<SavedTab> = emptyList(),
  /** Index into [tabs] of the tab that was showing. */
  val active: Int = 0,
)

object TabSession {
  private val json = Json { ignoreUnknownKeys = true }

  fun encode(session: SavedSession): String = json.encodeToString(SavedSession.serializer(), session)

  fun decode(text: String?): SavedSession? =
    text?.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(SavedSession.serializer(), it) }.getOrNull() }
      ?.let { s -> s.copy(active = s.active.coerceIn(0, (s.tabs.size - 1).coerceAtLeast(0))) }

  fun file(dir: File) = File(dir, "browser_tabs.json")

  fun read(dir: File): SavedSession? = runCatching { file(dir).takeIf { it.exists() }?.readText() }.getOrNull().let(::decode)

  fun write(
    dir: File,
    session: SavedSession,
  ) {
    runCatching {
      val target = file(dir)
      if (session.tabs.isEmpty()) {
        target.delete()
        return
      }
      val tmp = File(dir, "browser_tabs.json.tmp")
      tmp.writeText(encode(session))
      if (!tmp.renameTo(target)) {
        target.delete()
        tmp.renameTo(target)
      }
    }
  }
}
