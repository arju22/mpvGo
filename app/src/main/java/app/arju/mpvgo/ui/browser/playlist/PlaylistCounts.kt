package app.arju.mpvgo.ui.browser.playlist

/**
 * mpvGo: playlist item counting without per-playlist folder scans and O(items x videos) lookups.
 */
internal object PlaylistCounts {
  /** M3U items are URLs and always count; local items count only when their path is still available. */
  fun countAvailable(
    isM3u: Boolean,
    itemPaths: List<String>,
    availablePaths: Set<String>,
  ): Int = if (isM3u) itemPaths.size else itemPaths.count { it in availablePaths }
}
