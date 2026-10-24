package app.arju.mpvgo.domain.playbackstate.repository

import app.arju.mpvgo.database.entities.PlaybackStateEntity

interface PlaybackStateRepository {
  suspend fun upsert(playbackState: PlaybackStateEntity)

  suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity?

  /** mpvGo: batch lookup keyed by mediaTitle (avoids one query per list item). */
  suspend fun getVideoDataByTitles(mediaTitles: Collection<String>): Map<String, PlaybackStateEntity> =
    mediaTitles.toSet().mapNotNull { t -> getVideoDataByTitle(t)?.let { t to it } }.toMap()

  suspend fun clearAllPlaybackStates()

  suspend fun deleteByTitle(mediaTitle: String)

  suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  )
}
