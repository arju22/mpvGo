package app.arju.mpvgo.ui.browser.folderlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** mpvGo: O(1) new-video count lookup per folder row (was a list find{} per row per recomposition). */
@Composable
internal fun rememberNewCounts(list: List<FolderWithNewCount>): Map<String, Int> =
  remember(list) { newCountsByBucket(list) }

internal fun newCountsByBucket(list: List<FolderWithNewCount>): Map<String, Int> {
  val map = HashMap<String, Int>(list.size)
  for (item in list) map.putIfAbsent(item.folder.bucketId, item.newVideoCount)
  return map
}
