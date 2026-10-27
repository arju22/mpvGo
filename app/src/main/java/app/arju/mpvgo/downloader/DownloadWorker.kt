package app.arju.mpvgo.downloader

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.delay
import org.koin.core.context.GlobalContext

/**
 * Picks the queue back up after the task is swiped away, the process dies, or a foreground-service
 * start isn't allowed. WorkManager also re-runs it after a reboot.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val manager = GlobalContext.get().get<DownloadManager>()
    if (!manager.hasPending()) return Result.success()
    runCatching { setForeground(getForegroundInfo()) }
    manager.restore().join()
    delay(2000)
    while (manager.running.value.isNotEmpty()) delay(2000)
    return Result.success()
  }

  override suspend fun getForegroundInfo(): ForegroundInfo {
    DownloadNotifications.createChannels(applicationContext)
    val manager = GlobalContext.get().get<DownloadManager>()
    return ForegroundInfo(
      DownloadNotifications.ONGOING_ID + 1,
      DownloadNotifications.ongoing(applicationContext, manager.live.value.values, manager.running.value.size, false),
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )
  }

  companion object {
    private const val NAME = "mpvgo-downloads-resume"

    fun enqueue(context: Context) {
      val request = OneTimeWorkRequestBuilder<DownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
      WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
    }
  }
}
