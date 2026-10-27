package app.arju.mpvgo.core.database.repository

import app.arju.mpvgo.core.database.entities.PlaybackStateEntity
import app.arju.mpvgo.core.database.MpvGoDatabase
import app.arju.mpvgo.core.domain.playbackstate.repository.PlaybackStateRepository

class PlaybackStateRepositoryImpl(
  private val database: MpvGoDatabase,
) : PlaybackStateRepository {
  override suspend fun upsert(playbackState: PlaybackStateEntity) {
    database.videoDataDao().upsert(playbackState)
  }

  override suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity? =
    database.videoDataDao().getVideoDataByTitle(mediaTitle)

  override suspend fun getVideoDataByTitles(mediaTitles: Collection<String>): Map<String, PlaybackStateEntity> =
    mediaTitles.toSet().chunked(900).flatMap { database.videoDataDao().getVideoDataByTitles(it) }
      .associateBy { it.mediaTitle }

  override suspend fun clearAllPlaybackStates() {
    database.videoDataDao().clearAllPlaybackStates()
  }

  override suspend fun deleteByTitle(mediaTitle: String) {
    database.videoDataDao().deleteByTitle(mediaTitle)
  }

  override suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  ) {
    database.videoDataDao().updateMediaTitle(oldTitle, newTitle)
  }
}
