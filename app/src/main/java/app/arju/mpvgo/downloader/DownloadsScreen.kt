package app.arju.mpvgo.downloader

import androidx.compose.runtime.Composable
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.downloader.ui.DownloadsContent
import kotlinx.serialization.Serializable

/** Downloads tab (replaces the old Network tab). */
@Serializable
object DownloadsScreen : Screen {
  @Composable
  override fun Content() {
    DownloadsContent()
  }
}
