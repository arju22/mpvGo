package app.arju.mpvgo.downloader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.settings.PreferencesScreen
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.library.components.BrowserTopBar
import app.arju.mpvgo.library.states.EmptyState
import app.arju.mpvgo.webbrowser.GoBrowserFab
import app.arju.mpvgo.webbrowser.openGoBrowser
import kotlinx.serialization.Serializable

/** Downloads tab (replaces the old Network tab). The download list arrives with the downloader. */
@Serializable
object DownloadsScreen : Screen {
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        BrowserTopBar(
          title = "Downloads",
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onCancelSelection = {},
          onSettingsClick = { backstack.add(PreferencesScreen) },
          onBrowserClick = { backstack.openGoBrowser() },
        )
      },
    ) { padding ->
      EmptyState(
        icon = Icons.Filled.Download,
        title = "No downloads yet",
        message = "Videos you download from Go Browser or a link appear here.",
        modifier = Modifier.fillMaxSize().padding(padding),
      )
      Box(Modifier.fillMaxSize().padding(padding)) {
        GoBrowserFab(
          visible = true,
          onClick = { backstack.openGoBrowser() },
          modifier = Modifier.align(Alignment.BottomStart),
        )
      }
    }
  }
}
