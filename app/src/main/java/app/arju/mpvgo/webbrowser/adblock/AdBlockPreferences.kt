package app.arju.mpvgo.webbrowser.adblock

import app.arju.mpvgo.core.preferences.preference.PreferenceStore

class AdBlockPreferences(store: PreferenceStore) {
  val enabled = store.getBoolean("adblock_enabled", true)
  val blockPopups = store.getBoolean("adblock_block_popups", true)
  val cosmetic = store.getBoolean("adblock_cosmetic", true)
  val enabledLists = store.getStringSet("adblock_lists", FilterLists.DEFAULT_ENABLED)
  val customListUrls = store.getStringSet("adblock_custom_lists", emptySet())
  val myFilters = store.getString("adblock_my_filters", "")
  val allowedSites = store.getStringSet("adblock_allowed_sites", emptySet())
  val autoUpdateWifiOnly = store.getBoolean("adblock_update_wifi_only", true)
  val lastUpdated = store.getLong("adblock_last_updated", 0L)
}
