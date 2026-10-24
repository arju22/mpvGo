package app.arju.mpvgo.preferences

import app.arju.mpvgo.preferences.preference.PreferenceStore
import app.arju.mpvgo.ui.player.controls.TapZones

/**
 * mpvGo gesture settings: the center tap area (ellipse) and bottom strip, used when
 * "single tap for center" is on, plus subtitle gestures.
 */
class TapZonePreferences(
  preferenceStore: PreferenceStore,
) {
  val centerAreaWidth = preferenceStore.getInt("center_tap_area_width", TapZones.DEFAULT_SIZE)
  val centerAreaHeight = preferenceStore.getInt("center_tap_area_height", TapZones.DEFAULT_SIZE)
  val centerAreaX = preferenceStore.getInt("center_tap_area_cx", TapZones.DEFAULT_CENTER)
  val centerAreaY = preferenceStore.getInt("center_tap_area_cy", TapZones.DEFAULT_CENTER)
  val bottomStripShowsControls = preferenceStore.getBoolean("bottom_strip_shows_controls", true)
  val subtitleGestures = preferenceStore.getBoolean("subtitle_gestures", true)
}
