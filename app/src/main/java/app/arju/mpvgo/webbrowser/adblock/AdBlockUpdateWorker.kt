package app.arju.mpvgo.webbrowser.adblock

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/** Weekly filter list update. Scheduled only once the browser has been opened. */
class AdBlockUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val adBlocker = GlobalContext.get().get<AdBlocker>()
    return if (adBlocker.update(force = true)) Result.success() else Result.retry()
  }

  companion object {
    private const val NAME = "adblock_list_update"

    fun schedule(
      context: Context,
      wifiOnly: Boolean,
      replace: Boolean = false,
    ) {
      val request =
        PeriodicWorkRequestBuilder<AdBlockUpdateWorker>(7, TimeUnit.DAYS)
          .setInitialDelay(7, TimeUnit.DAYS)
          .setConstraints(
            Constraints
              .Builder()
              .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
              .setRequiresBatteryNotLow(true)
              .build(),
          ).build()
      WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        NAME,
        if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
        request,
      )
    }
  }
}
