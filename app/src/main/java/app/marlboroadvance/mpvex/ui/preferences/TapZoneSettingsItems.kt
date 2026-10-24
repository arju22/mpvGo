package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.TapZonePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

/** Settings › Gestures entries under "Single tap for center"; enabled only while it is on. */
@Composable
fun TapZoneSettingsItems(enabled: Boolean) {
  val preferences = koinInject<TapZonePreferences>()
  val backstack = LocalBackStack.current
  val width by preferences.centerAreaWidth.collectAsState()
  val height by preferences.centerAreaHeight.collectAsState()
  val bottomStrip by preferences.bottomStripShowsControls.collectAsState()

  PreferenceDivider()
  Preference(
    title = { Text(stringResource(R.string.pref_center_tap_area_title)) },
    enabled = enabled,
    summary = {
      Text(
        stringResource(R.string.pref_center_tap_area_summary, width, height),
        color = MaterialTheme.colorScheme.outline,
      )
    },
    onClick = { backstack.add(CenterTapAreaEditorScreen) },
  )
  PreferenceDivider()
  SwitchPreference(
    value = bottomStrip,
    onValueChange = { preferences.bottomStripShowsControls.set(it) },
    enabled = enabled,
    title = { Text(stringResource(R.string.pref_bottom_strip_shows_controls_title)) },
    summary = {
      Text(
        stringResource(R.string.pref_bottom_strip_shows_controls_summary),
        color = MaterialTheme.colorScheme.outline,
      )
    },
  )
  PreferenceDivider()
  Preference(
    title = { Text(stringResource(R.string.pref_tap_outside_center_title)) },
    enabled = enabled,
    summary = {
      Text(
        stringResource(R.string.pref_tap_outside_center_summary),
        color = MaterialTheme.colorScheme.outline,
      )
    },
  )
}
