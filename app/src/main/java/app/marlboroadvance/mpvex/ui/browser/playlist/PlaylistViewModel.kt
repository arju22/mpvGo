package app.marlboroadvance.mpvex.ui.browser.playlist

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.marlboroadvance.mpvex.database.entities.PlaylistEntity
import app.marlboroadvance.mpvex.database.repository.PlaylistRepository
import app.marlboroadvance.mpvex.repository.MediaFileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

data class PlaylistWithCount(
  val playlist: PlaylistEntity,
  val itemCount: Int,
)

class PlaylistViewModel(
  application: Application,
) : androidx.lifecycle.AndroidViewModel(application),
  KoinComponent {
  private val repository: PlaylistRepository by inject()
  // Using MediaFileRepository singleton directly

  private val _playlistsWithCount = MutableStateFlow<List<PlaylistWithCount>>(emptyList())
  val playlistsWithCount: StateFlow<List<PlaylistWithCount>> = _playlistsWithCount.asStateFlow()

  private val _isLoading = MutableStateFlow(false)
  val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

  // Track if initial load has completed to prevent empty state flicker
  private val _hasCompletedInitialLoad = MutableStateFlow(false)
  val hasCompletedInitialLoad: StateFlow<Boolean> = _hasCompletedInitialLoad.asStateFlow()

  companion object {
    private const val TAG = "PlaylistViewModel"

    fun factory(application: Application) =
      object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlaylistViewModel(application) as T
      }
  }

  init {
    // Load cached playlists instantly for immediate display
    viewModelScope.launch(Dispatchers.IO) {
      try {
        // Get initial cached data synchronously
        val cachedPlaylists = repository.getAllPlaylists()
        if (cachedPlaylists.isNotEmpty()) {
          // Show cached data immediately (without video counts for speed)
          val quickLoad = cachedPlaylists.sortedBy { it.name.lowercase() }.map { playlist ->
            PlaylistWithCount(playlist, 0) // Show 0 count initially
          }
          _playlistsWithCount.value = quickLoad
          _hasCompletedInitialLoad.value = true
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error loading cached playlists", e)
      }
    }

    // Then observe for updates with actual counts
    viewModelScope.launch(Dispatchers.IO) {
      repository.observeAllPlaylists().collectLatest { playlistsFromDb ->
        _playlistsWithCount.value = withActualCounts(playlistsFromDb.sortedBy { it.name.lowercase() })
        _hasCompletedInitialLoad.value = true
      }
    }
  }

  /**
   * mpvGo: count available items for all playlists with one folder pass over the union of their
   * buckets and Set lookups (was: per-playlist scans plus an O(items x videos) any{} search).
   */
  private suspend fun withActualCounts(playlists: List<PlaylistEntity>): List<PlaylistWithCount> {
    val itemPaths = playlists.associate { p -> p.id to repository.getPlaylistItems(p.id).map { it.filePath } }
    val buckets = playlists
      .filterNot { it.isM3uPlaylist }
      .flatMapTo(HashSet()) { p -> itemPaths[p.id].orEmpty().map { File(it).parent ?: "" } }
    val available = if (buckets.isEmpty()) {
      emptySet()
    } else {
      MediaFileRepository.getVideosForBuckets(getApplication(), buckets).mapTo(HashSet()) { it.path }
    }
    return playlists.map { p ->
      PlaylistWithCount(p, PlaylistCounts.countAvailable(p.isM3uPlaylist, itemPaths[p.id].orEmpty(), available))
    }
  }

  fun refresh() {
    viewModelScope.launch(Dispatchers.IO) {
      try {
        _isLoading.value = true
        val playlistsFromDb = repository.getAllPlaylists()
        _playlistsWithCount.value = withActualCounts(playlistsFromDb.sortedBy { it.name.lowercase() })
      } catch (e: Exception) {
        Log.e(TAG, "Error refreshing playlists", e)
      } finally {
        _isLoading.value = false
      }
    }
  }

  suspend fun createPlaylist(name: String): Long {
    return repository.createPlaylist(name)
  }

  suspend fun createM3UPlaylist(url: String): Result<Long> {
    return repository.createM3UPlaylist(url)
  }

  suspend fun createM3UPlaylistFromFile(uri: android.net.Uri): Result<Long> {
    return repository.createM3UPlaylistFromFile(getApplication(), uri)
  }

  suspend fun refreshM3UPlaylist(playlistId: Int): Result<Unit> {
    return repository.refreshM3UPlaylist(playlistId)
  }

  suspend fun deletePlaylist(playlist: PlaylistEntity) {
    repository.deletePlaylist(playlist)
  }
}
