package app.arju.mpvgo.webbrowser

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.arju.mpvgo.core.presentation.Screen
import app.arju.mpvgo.core.ui.LocalBackStack
import app.arju.mpvgo.library.components.BrowserTopBar
import app.arju.mpvgo.library.states.EmptyState
import app.arju.mpvgo.webbrowser.adblock.AdBlocker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

/** Go Browser. Placeholder until the browser UI lands (phase 7). */
@Serializable
object GoBrowserScreen : Screen {
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val adBlocker = koinInject<AdBlocker>()
    val state by adBlocker.state.collectAsState()
    var selfTest by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { adBlocker.warmUp() }
    LaunchedEffect(state) {
      if (state is AdBlocker.State.Ready) {
        selfTest =
          withContext(Dispatchers.Default) {
            val ad = adBlocker.shouldBlock("https://ad.doubleclick.net/ad.js", "https://example.com/", "script")
            val ok = adBlocker.shouldBlock("https://example.com/app.js", "https://example.com/", "script")
            "Test: ad ${if (ad) "blocked" else "allowed"}, page script ${if (ok) "blocked" else "allowed"}"
          }
      }
    }
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
      val adBlock =
        when (val s = state) {
          AdBlocker.State.Idle, AdBlocker.State.Loading -> "Ad block: loading filter lists…"
          is AdBlocker.State.Ready -> "Ad block: ready (${s.lists} lists). $selfTest"
          AdBlocker.State.NoLists -> "Ad block: filter lists not downloaded yet."
          AdBlocker.State.Unavailable -> "Ad block: not available on this device."
        }
      EmptyState(
        icon = Icons.Filled.Language,
        title = "Go Browser",
        message = "The web browser is on its way.\n$adBlock",
        modifier = Modifier.fillMaxSize().padding(padding),
      )
    }
  }
}
