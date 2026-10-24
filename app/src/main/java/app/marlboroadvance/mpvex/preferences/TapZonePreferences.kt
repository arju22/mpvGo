package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import app.marlboroadvance.mpvex.ui.player.controls.TapZones

/** Center tap area (ellipse) and bottom strip settings, used when "single tap for center" is on. */
class TapZonePreferences(
  preferenceStore: PreferenceStore,
) {
  val centerAreaWidth = preferenceStore.getInt("center_tap_area_width", TapZones.DEFAULT_SIZE)
  val centerAreaHeight = preferenceStore.getInt("center_tap_area_height", TapZones.DEFAULT_SIZE)
  val centerAreaX = preferenceStore.getInt("center_tap_area_cx", TapZones.DEFAULT_CENTER)
  val centerAreaY = preferenceStore.getInt("center_tap_area_cy", TapZones.DEFAULT_CENTER)
  val bottomStripShowsControls = preferenceStore.getBoolean("bottom_strip_shows_controls", true)
}
