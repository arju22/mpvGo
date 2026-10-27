package app.arju.mpvgo.downloader.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
  @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<DownloadEntity>>

  @Query("SELECT * FROM downloads WHERE id = :id")
  fun observe(id: Long): Flow<DownloadEntity?>

  @Query("SELECT COUNT(*) FROM downloads WHERE status IN ('Queued', 'Running')")
  fun observeActiveCount(): Flow<Int>

  @Query("SELECT * FROM downloads WHERE id = :id")
  suspend fun get(id: Long): DownloadEntity?

  @Query("SELECT * FROM downloads WHERE status IN ('Queued', 'Running') ORDER BY priority DESC, createdAt ASC")
  suspend fun pending(): List<DownloadEntity>

  @Query("SELECT * FROM downloads WHERE id IN (:ids)")
  suspend fun getAll(ids: Collection<Long>): List<DownloadEntity>

  @Insert
  suspend fun insert(entity: DownloadEntity): Long

  @Update
  suspend fun update(entity: DownloadEntity)

  @Query("UPDATE downloads SET downloadedBytes = :downloaded, totalBytes = :total, partsState = :parts, segmentsDone = :segDone, segmentsTotal = :segTotal WHERE id = :id")
  suspend fun updateProgress(id: Long, downloaded: Long, total: Long, parts: String?, segDone: Int, segTotal: Int)

  @Query("UPDATE downloads SET status = :status WHERE id = :id")
  suspend fun setStatus(id: Long, status: DownloadStatus)

  /** Anything left Running by a killed process is resumed as Queued. */
  @Query("UPDATE downloads SET status = 'Queued' WHERE status = 'Running'")
  suspend fun requeueInterrupted()

  @Query("DELETE FROM downloads WHERE id IN (:ids)")
  suspend fun delete(ids: Collection<Long>)
}
