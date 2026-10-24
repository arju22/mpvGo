package app.arju.mpvgo

import android.app.Application
import app.arju.mpvgo.database.repository.VideoMetadataCacheRepository
import app.arju.mpvgo.di.DatabaseModule
import app.arju.mpvgo.di.FileManagerModule
import app.arju.mpvgo.di.PreferencesModule
import app.arju.mpvgo.presentation.crash.CrashActivity
import app.arju.mpvgo.presentation.crash.GlobalExceptionHandler
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.annotation.KoinExperimentalAPI

@OptIn(KoinExperimentalAPI::class)
class App : Application() {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val metadataCache: VideoMetadataCacheRepository by inject()

  override fun onCreate() {
    super.onCreate()

    // Initialize Koin
    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        app.arju.mpvgo.di.domainModule,
      )
    }

    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

    FastThumbnails.initialize(this)

    // mpvGo: cache maintenance stats every cached file; keep it off the startup path and run it
    // at most once a day.
    applicationScope.launch {
      delay(MAINTENANCE_DELAY_MS)
      val prefs = getSharedPreferences("mpvGo_maintenance", MODE_PRIVATE)
      val now = System.currentTimeMillis()
      if (now - prefs.getLong("last_cache_maintenance", 0L) < MAINTENANCE_INTERVAL_MS) return@launch
      runCatching {
        metadataCache.performMaintenance()
        prefs.edit().putLong("last_cache_maintenance", now).apply()
      }
    }
    // No full-storage media scan on launch: MediaProvider indexes new files itself on Android 11+,
    // and pull-to-refresh in folder views indexes any videos MediaStore is missing.
  }

  private companion object {
    const val MAINTENANCE_DELAY_MS = 15_000L
    const val MAINTENANCE_INTERVAL_MS = 24 * 60 * 60 * 1000L
  }
}
