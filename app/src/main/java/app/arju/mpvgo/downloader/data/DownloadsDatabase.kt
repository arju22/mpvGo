package app.arju.mpvgo.downloader.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/** Kept apart from the main library database so the downloader never forces a library migration. */
@Database(entities = [DownloadEntity::class], version = 1, exportSchema = true)
@TypeConverters(DownloadConverters::class)
abstract class DownloadsDatabase : RoomDatabase() {
  abstract fun downloadDao(): DownloadDao

  companion object {
    fun create(context: Context): DownloadsDatabase =
      Room.databaseBuilder(context, DownloadsDatabase::class.java, "downloads.db")
        .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
        .build()
  }
}
