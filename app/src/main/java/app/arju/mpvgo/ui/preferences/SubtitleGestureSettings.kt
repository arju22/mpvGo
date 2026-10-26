package app.arju.mpvgo.ui.preferences

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import app.arju.mpvgo.R
import app.arju.mpvgo.preferences.TapZonePreferences
import app.arju.mpvgo.preferences.preference.collectAsState
import me.zhanghai.compose.preference.SwitchPreference
import org.koin.compose.koinInject

/** Settings › Subtitles › Gestures section (header + card). */
@Composable
fun SubtitleGestureSettingsSection() {
  val preferences = koinInject<TapZonePreferences>()
  val enabled by preferences.subtitleGestures.collectAsState()

  PreferenceSectionHeader(title = stringResource(R.string.pref_gesture))
  PreferenceCard {
    SwitchPreference(
      value = enabled,
      onValueChange = { preferences.subtitleGestures.set(it) },
      title = { Text(stringResource(R.string.pref_subtitle_gestures_title)) },
      summary = {
        Text(
          stringResource(R.string.pref_subtitle_gestures_summary),
          color = MaterialTheme.colorScheme.outline,
        )
      },
    )
  }
}
