package app.arju.mpvgo.webbrowser

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

fun NavBackStack<Screen>.openGoBrowser(url: String? = null) {
  val last = lastOrNull()
  if (last is GoBrowserScreen && url == null) return
  add(GoBrowserScreen(url))
}

/** Web links opened from other apps, when "Open links from other apps" is on. */
object BrowserLinks {
  const val ALIAS = "app.arju.mpvgo.GoBrowserLinks"

  /** The http(s) link of a VIEW intent sent to the browser alias, or null. Clears it so it's handled once. */
  fun linkFrom(intent: Intent?): String? {
    if (intent?.action != Intent.ACTION_VIEW) return null
    val uri = intent.data ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
    if (intent.component?.className != ALIAS) return null
    intent.data = null
    return uri.toString()
  }

  fun isEnabled(context: Context): Boolean =
    context.packageManager.getComponentEnabledSetting(ComponentName(context, ALIAS)) ==
      PackageManager.COMPONENT_ENABLED_STATE_ENABLED

  fun setEnabled(
    context: Context,
    enabled: Boolean,
  ) {
    context.packageManager.setComponentEnabledSetting(
      ComponentName(context, ALIAS),
      if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
      PackageManager.DONT_KILL_APP,
    )
  }
}

/** Replaces any open browser with one showing [url], so Back leaves to where the user was. */
fun NavBackStack<Screen>.openLinkInBrowser(url: String) {
  removeAll { it is GoBrowserScreen }
  add(GoBrowserScreen(url))
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

/** Shared FAB insets so Home Play FAB, globe FAB, and Browser video FAB align pixel-perfectly. */
object FabDefaults {
  val OuterPadding = 16.dp
  val MenuInset = 16.dp
  val EdgeInset = OuterPadding + MenuInset // 32.dp
  val BottomInset = OuterPadding + MenuInset // 32.dp
}

/** Bottom-left FAB on Home and Downloads, mirroring the Play FAB on the right. */
@Composable
fun GoBrowserFab(
  visible: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  alignWithFabMenu: Boolean = false,
) {
  AnimatedVisibility(
    visible = visible,
    enter = scaleIn() + fadeIn(),
    exit = scaleOut() + fadeOut(),
    modifier =
      modifier.padding(
        // FloatingActionButtonMenu adds its own 16dp inset around the Play FAB; mirror it.
        start = if (alignWithFabMenu) FabDefaults.EdgeInset else FabDefaults.OuterPadding,
        bottom = (if (alignWithFabMenu) FabDefaults.BottomInset else FabDefaults.OuterPadding) + MainFabBottomPadding,
      ),
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
