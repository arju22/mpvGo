package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.marlboroadvance.mpvex.preferences.GesturePreferences
import app.marlboroadvance.mpvex.preferences.TapZonePreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState

@Composable
fun rememberTapZoneConfig(
  gesturePreferences: GesturePreferences,
  tapZonePreferences: TapZonePreferences,
): TapZoneConfig {
  val sideWidth by gesturePreferences.doubleTapSeekAreaWidth.collectAsState()
  val singleTapForCenter by gesturePreferences.useSingleTapForCenter.collectAsState()
  val width by tapZonePreferences.centerAreaWidth.collectAsState()
  val height by tapZonePreferences.centerAreaHeight.collectAsState()
  val cx by tapZonePreferences.centerAreaX.collectAsState()
  val cy by tapZonePreferences.centerAreaY.collectAsState()
  val bottomStrip by tapZonePreferences.bottomStripShowsControls.collectAsState()
  return remember(sideWidth, singleTapForCenter, width, height, cx, cy, bottomStrip) {
    TapZoneConfig(sideWidth, singleTapForCenter, width, height, cx, cy, bottomStrip)
  }
}
