package app.arju.mpvgo.downloader

import app.arju.mpvgo.downloader.data.DownloadPreferences
import app.arju.mpvgo.downloader.data.DownloadsDatabase
import app.arju.mpvgo.downloader.engine.DownloadFinalizer
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val DownloaderModule =
  module {
    single { DownloadsDatabase.create(androidContext()) }
    single { get<DownloadsDatabase>().downloadDao() }
    singleOf(::DownloadPreferences)
    single { DownloadFinalizer(androidContext()) }
    single { DownloadStreamServer(get()) }
    single {
      val context = androidContext()
      val prefs = get<DownloadPreferences>()
      DownloadManager(context, get(), prefs, get()).apply {
        onFinished = { if (prefs.notifyWhenFinished.get()) DownloadNotifications.finished(context, it) }
        onFailed = { DownloadNotifications.failed(context, it) }
      }
    }
  }
