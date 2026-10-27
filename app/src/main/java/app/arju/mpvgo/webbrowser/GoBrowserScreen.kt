package app.arju.mpvgo.webbrowser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.library.components.BrowserTopBar
import app.arju.mpvgo.library.states.EmptyState
import kotlinx.serialization.Serializable

/** Go Browser. Placeholder until the browser UI lands (phase 7). */
@Serializable
object GoBrowserScreen : Screen {
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    Scaffold(
      topBar = {
        BrowserTopBar(
          title = "Go Browser",
          isInSelectionMode = false,
          selectedCount = 0,
          totalCount = 0,
          onCancelSelection = {},
          onBackClick = { backstack.removeLastOrNull() },
        )
      },
    ) { padding ->
      EmptyState(
        icon = Icons.Filled.Language,
        title = "Go Browser",
        message = "The web browser is on its way.",
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    }
  }
}
