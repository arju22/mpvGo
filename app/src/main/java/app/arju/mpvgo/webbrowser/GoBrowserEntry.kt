package app.arju.mpvgo.webbrowser

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavBackStack
import app.arju.mpvgo.core.presentation.Screen

/** Distance of the main Play FAB from the bottom of its Scaffold (clears the navigation bar). */
val MainFabBottomPadding = 88.dp

fun NavBackStack<Screen>.openGoBrowser() {
  if (lastOrNull() != GoBrowserScreen) add(GoBrowserScreen)
}

/** Top-bar entry to Go Browser: the old Network tab globe with a thin ring, placed before Settings. */
@Composable
fun GoBrowserTopBarButton(onClick: () -> Unit) {
  IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp)) {
    val tint = MaterialTheme.colorScheme.secondary
    Box(
      modifier = Modifier.size(28.dp).border(1.5.dp, tint, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.Filled.Language, contentDescription = "Go Browser", modifier = Modifier.size(22.dp), tint = tint)
    }
  }
}

/** Bottom-left FAB on Home and Downloads, mirroring the Play FAB on the right. */
@Composable
fun GoBrowserFab(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
  AnimatedVisibility(
    visible = visible,
    enter = scaleIn() + fadeIn(),
    exit = scaleOut() + fadeOut(),
    modifier = modifier.padding(start = 16.dp, bottom = 16.dp + MainFabBottomPadding),
  ) {
    FloatingActionButton(
      onClick = onClick,
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
      Icon(Icons.Filled.Language, contentDescription = "Go Browser")
    }
  }
}
