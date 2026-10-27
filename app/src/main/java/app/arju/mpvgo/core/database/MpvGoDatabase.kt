package app.arju.mpvgo.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.arju.mpvgo.core.database.converters.NetworkProtocolConverter
import app.arju.mpvgo.core.database.dao.NetworkConnectionDao
import app.arju.mpvgo.core.database.dao.PlaybackStateDao
import app.arju.mpvgo.core.database.dao.PlaylistDao
import app.arju.mpvgo.core.database.dao.RecentlyPlayedDao
import app.arju.mpvgo.core.database.dao.VideoMetadataDao
import app.arju.mpvgo.core.database.entities.PlaybackStateEntity
import app.arju.mpvgo.core.database.entities.PlaylistEntity
import app.arju.mpvgo.core.database.entities.PlaylistItemEntity
import app.arju.mpvgo.core.database.entities.RecentlyPlayedEntity
import app.arju.mpvgo.core.database.entities.VideoMetadataEntity
import app.arju.mpvgo.core.domain.network.NetworkConnection

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
