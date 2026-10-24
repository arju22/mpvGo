package app.arju.mpvgo.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.arju.mpvgo.database.converters.NetworkProtocolConverter
import app.arju.mpvgo.database.dao.NetworkConnectionDao
import app.arju.mpvgo.database.dao.PlaybackStateDao
import app.arju.mpvgo.database.dao.PlaylistDao
import app.arju.mpvgo.database.dao.RecentlyPlayedDao
import app.arju.mpvgo.database.dao.VideoMetadataDao
import app.arju.mpvgo.database.entities.PlaybackStateEntity
import app.arju.mpvgo.database.entities.PlaylistEntity
import app.arju.mpvgo.database.entities.PlaylistItemEntity
import app.arju.mpvgo.database.entities.RecentlyPlayedEntity
import app.arju.mpvgo.database.entities.VideoMetadataEntity
import app.arju.mpvgo.domain.network.NetworkConnection

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    VideoMetadataEntity::class,
    NetworkConnection::class,
    PlaylistEntity::class,
    PlaylistItemEntity::class,
  ],
  version = 8,
  exportSchema = true,
)
@TypeConverters(NetworkProtocolConverter::class)
abstract class MpvGoDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun videoMetadataDao(): VideoMetadataDao

  abstract fun networkConnectionDao(): NetworkConnectionDao

  abstract fun playlistDao(): PlaylistDao
}
