package app.arju.mpvgo.webbrowser.library

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A bookmark, or a folder when [isFolder]. [parentId] 0 is the root ("All bookmarks"). */
@Entity(tableName = "bookmarks", indices = [Index("parentId"), Index("url")])
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val parentId: Long = ROOT,
  val isFolder: Boolean = false,
  val title: String,
  val url: String = "",
  val createdAt: Long = System.currentTimeMillis(),
) {
  companion object {
    const val ROOT = 0L
  }
}

/** One visited page. Repeat visits within a short time update the same row. */
@Entity(tableName = "history", indices = [Index("visitedAt"), Index("url")])
data class HistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val url: String,
  val title: String,
  val visitedAt: Long,
  /** A video from this page was played in mpvGo. */
  val played: Boolean = false,
)

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM bookmarks")
  fun observeAll(): Flow<List<BookmarkEntity>>

  @Query("SELECT * FROM bookmarks")
  suspend fun all(): List<BookmarkEntity>

  @Query("SELECT * FROM bookmarks WHERE isFolder = 0 AND url = :url LIMIT 1")
  suspend fun byUrl(url: String): BookmarkEntity?

  @Query("SELECT * FROM bookmarks WHERE isFolder = 0 AND url = :url LIMIT 1")
  fun observeByUrl(url: String): Flow<BookmarkEntity?>

  @Query("SELECT * FROM bookmarks WHERE isFolder = 1 AND parentId = :parentId AND title = :title LIMIT 1")
  suspend fun folder(
    parentId: Long,
    title: String,
  ): BookmarkEntity?

  @Insert
  suspend fun insert(item: BookmarkEntity): Long

  @Update
  suspend fun update(item: BookmarkEntity)

  @Query("DELETE FROM bookmarks WHERE id IN (:ids)")
  suspend fun delete(ids: List<Long>)

  @Query("SELECT id FROM bookmarks WHERE parentId IN (:parents)")
  suspend fun childIds(parents: List<Long>): List<Long>

  @Query("SELECT * FROM bookmarks WHERE isFolder = 0 AND (url LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%') LIMIT :limit")
  suspend fun search(
    q: String,
    limit: Int,
  ): List<BookmarkEntity>
}

@Dao
interface HistoryDao {
  @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
  fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

  @Query("SELECT * FROM history WHERE url = :url AND visitedAt >= :since ORDER BY visitedAt DESC LIMIT 1")
  suspend fun recentVisit(
    url: String,
    since: Long,
  ): HistoryEntity?

  @Insert
  suspend fun insert(item: HistoryEntity): Long

  @Update
  suspend fun update(item: HistoryEntity)

  @Query("UPDATE history SET played = 1 WHERE id = (SELECT id FROM history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1)")
  suspend fun markPlayed(url: String)

  @Query("DELETE FROM history WHERE id IN (:ids)")
  suspend fun delete(ids: List<Long>)

  @Query("DELETE FROM history WHERE visitedAt >= :since")
  suspend fun deleteSince(since: Long)

  @Query("DELETE FROM history WHERE visitedAt < :before")
  suspend fun deleteBefore(before: Long)

  @Query(
    "SELECT * FROM history WHERE url LIKE '%' || :q || '%' OR title LIKE '%' || :q || '%' " +
      "GROUP BY url ORDER BY MAX(visitedAt) DESC LIMIT :limit",
  )
  suspend fun search(
    q: String,
    limit: Int,
  ): List<HistoryEntity>
}

/** Bookmarks and history of the Go browser, kept apart from the library database. */
@Database(entities = [BookmarkEntity::class, HistoryEntity::class], version = 1, exportSchema = true)
abstract class BrowserLibraryDatabase : RoomDatabase() {
  abstract fun bookmarks(): BookmarkDao

  abstract fun history(): HistoryDao

  companion object {
    fun create(context: Context): BrowserLibraryDatabase =
      Room.databaseBuilder(context, BrowserLibraryDatabase::class.java, "browser.db")
        .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
        .build()
  }
}
