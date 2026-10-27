package app.arju.mpvgo.downloader

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Keeps the process alive (dataSync foreground service) only while downloads are running.
 * The queue itself lives in [DownloadManager]; this just shows progress, holds locks and stops itself.
 */
class DownloadService : Service() {
  private val manager by inject<DownloadManager>()
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null
  private var foreground = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    DownloadNotifications.createChannels(this)
    goForeground()
    scope.launch {
      combine(manager.running, manager.live, manager.waitingForWifi) { r, l, w -> Triple(r, l, w) }
        .conflate()
        .collect { (running, live, waiting) ->
          if (running.isEmpty()) {
            releaseLocks()
            delay(1500)
            if (manager.running.value.isEmpty()) {
              stopForeground(STOP_FOREGROUND_REMOVE)
              stopSelf()
            }
          } else {
            acquireLocks()
            notifyProgress(live.filterKeys { it in running }.values, running.size, waiting)
            delay(1000)
          }
        }
    }
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    goForeground()
    val id = intent?.getLongExtra(EXTRA_ID, -1L) ?: -1L
    when (intent?.action) {
      ACTION_PAUSE_ALL -> manager.pauseAll()
      ACTION_CANCEL_ALL -> manager.cancelAll()
      ACTION_RETRY -> if (id >= 0) {
        DownloadNotifications.cancel(this, id)
        manager.retry(listOf(id))
      }
    }
    return START_NOT_STICKY
  }

  override fun onTimeout(startId: Int, fgsType: Int) {
    manager.pauseForSystemLimit()
    DownloadNotifications.pausedBySystem(this)
    releaseLocks()
    stopForeground(STOP_FOREGROUND_REMOVE)
    stopSelf()
  }

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Some devices kill the process with the task; WorkManager picks the queue back up.
    if (manager.running.value.isNotEmpty()) DownloadWorker.enqueue(this)
    super.onTaskRemoved(rootIntent)
  }

  override fun onDestroy() {
    releaseLocks()
    scope.cancel()
    super.onDestroy()
  }

  private fun goForeground() {
    if (foreground) return
    runCatching {
      ServiceCompat.startForeground(
        this,
        DownloadNotifications.ONGOING_ID,
        DownloadNotifications.ongoing(this, manager.live.value.values, manager.running.value.size, false),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
      )
      foreground = true
    }.onFailure { stopSelf() }
  }

  private fun notifyProgress(live: Collection<LiveProgress>, count: Int, waiting: Boolean) {
    runCatching {
      getSystemService(android.app.NotificationManager::class.java)
        .notify(DownloadNotifications.ONGOING_ID, DownloadNotifications.ongoing(this, live, count, waiting))
    }
  }

  private fun acquireLocks() {
    if (wakeLock?.isHeld != true) {
      wakeLock = getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mpvGo:downloads")
        .apply { setReferenceCounted(false); acquire(30 * 60 * 1000L) }
    } else {
      wakeLock?.acquire(30 * 60 * 1000L)
    }
    if (wifiLock?.isHeld != true) {
      wifiLock = runCatching {
        getSystemService(WifiManager::class.java)
          .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "mpvGo:downloads")
          .apply { setReferenceCounted(false); acquire() }
      }.getOrNull()
    }
  }

  private fun releaseLocks() {
    runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
    runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
  }

  companion object {
    const val ACTION_PAUSE_ALL = "app.arju.mpvgo.downloads.PAUSE_ALL"
    const val ACTION_CANCEL_ALL = "app.arju.mpvgo.downloads.CANCEL_ALL"
    const val ACTION_RETRY = "app.arju.mpvgo.downloads.RETRY"
    const val EXTRA_ID = "id"

    fun start(context: Context) {
      runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        .onFailure { DownloadWorker.enqueue(context) }
    }
  }
}
