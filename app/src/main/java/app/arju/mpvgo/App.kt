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

    // Perform cache maintenance on app startup (non-blocking)
    applicationScope.launch {
      runCatching {
        metadataCache.performMaintenance()
      }
    }
    // No full-storage media scan on launch: MediaProvider indexes new files itself on Android 11+,
    // and the folder list's pull-to-refresh still triggers a full scan when needed.
  }
}
