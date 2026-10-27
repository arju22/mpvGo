package app.arju.mpvgo.downloader.data

import app.arju.mpvgo.core.preferences.preference.PreferenceStore

class DownloadPreferences(store: PreferenceStore) {
  /** Relative to shared storage, e.g. "Download/mpvGo" or "Movies/mpvGo". */
  val folder = store.getString("downloads_folder", DEFAULT_FOLDER)
  val deleteFileOnRemove = store.getBoolean("downloads_delete_file_on_remove", false)
  val concurrentDownloads = store.getInt("downloads_concurrent", 3)
  val connectionsPerFile = store.getInt("downloads_connections", 8)

  /** KB/s; 0 = unlimited. */
  val speedLimitKbps = store.getInt("downloads_speed_limit_kbps", 0)
  val wifiOnly = store.getBoolean("downloads_wifi_only", false)
  val autoRetry = store.getBoolean("downloads_auto_retry", true)
  val hlsToMp4 = store.getBoolean("downloads_hls_to_mp4", true)
  val notifyWhenFinished = store.getBoolean("downloads_notify_finished", true)
  val askBeforeEveryDownload = store.getBoolean("downloads_ask_every_time", true)
  val batteryPromptShown = store.getBoolean("downloads_battery_prompt_shown", false)

  companion object {
    const val DEFAULT_FOLDER = "Download/mpvGo"
    const val MAX_CONNECTIONS = 16
    val FOLDER_CHOICES = listOf("Download/mpvGo", "Movies/mpvGo", "Download", "Movies")
  }
}
