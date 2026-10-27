package app.arju.mpvgo.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import app.arju.mpvgo.MainActivity
import app.arju.mpvgo.downloader.data.DownloadEntity
import app.arju.mpvgo.player.PlayerActivity
import java.io.File

object DownloadNotifications {
  const val CHANNEL_PROGRESS = "mpvgo_downloads"
  const val CHANNEL_DONE = "mpvgo_downloads_done"
  const val ONGOING_ID = 7301
  const val EXTRA_OPEN_DOWNLOADS = "app.arju.mpvgo.extra.OPEN_DOWNLOADS"

  fun createChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW).apply {
        setShowBadge(false)
      },
    )
    nm.createNotificationChannel(
      NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT),
    )
  }

  fun ongoing(context: Context, live: Collection<LiveProgress>, count: Int, waitingForWifi: Boolean): Notification {
    val downloaded = live.sumOf { it.downloaded }
    val total = live.sumOf { if (it.total > 0) it.total else 0L }
    val speed = live.sumOf { it.speedBps }
    val hlsDone = live.sumOf { it.segmentsDone }
    val hlsTotal = live.sumOf { it.segmentsTotal }
    val title = when {
      count == 0 && waitingForWifi -> "Waiting for Wi-Fi"
      count <= 1 -> "Downloading 1 file"
      else -> "Downloading $count files"
    }
    val text = buildString {
      if (total > 0) append("${Formatter.formatShortFileSize(context, downloaded)} / ${Formatter.formatShortFileSize(context, total)}")
      else if (downloaded > 0) append(Formatter.formatShortFileSize(context, downloaded))
      if (speed > 0) append(" · ${Formatter.formatShortFileSize(context, speed)}/s")
    }
    val b = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle(title)
      .setContentText(text)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setCategory(NotificationCompat.CATEGORY_PROGRESS)
      .setContentIntent(openDownloads(context))
      .addAction(0, "Pause all", serviceAction(context, DownloadService.ACTION_PAUSE_ALL))
      .addAction(0, "Cancel all", serviceAction(context, DownloadService.ACTION_CANCEL_ALL))
    when {
      total > 0 -> b.setProgress(1000, (downloaded * 1000 / total).toInt(), false)
      hlsTotal > 0 -> b.setProgress(hlsTotal, hlsDone, false)
      count > 0 -> b.setProgress(0, 0, true)
    }
    return b.build()
  }

  fun pausedBySystem(context: Context) {
    notify(
      context,
      7302,
      NotificationCompat.Builder(context, CHANNEL_DONE)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle("Downloads paused")
        .setContentText("Downloads paused by Android background limit. Tap to resume.")
        .setContentIntent(openDownloads(context))
        .setAutoCancel(true)
        .build(),
    )
  }

  fun finished(context: Context, e: DownloadEntity) {
    val uri = contentUri(context, e)
    val b = NotificationCompat.Builder(context, CHANNEL_DONE)
      .setSmallIcon(android.R.drawable.stat_sys_download_done)
      .setContentTitle("Downloaded")
      .setContentText(e.fileName)
      .setAutoCancel(true)
      .setContentIntent(openDownloads(context))
    if (uri != null) {
      val play = Intent(Intent.ACTION_VIEW, uri, context, PlayerActivity::class.java)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply {
          e.finalPath?.let { putExtra("file_path", it) }
          if (e.isPrivate) putExtra(app.arju.mpvgo.player.MediaRequest.EXTRA_NO_RECENTS, true)
        }
      b.setContentIntent(PendingIntent.getActivity(context, e.id.toInt() * 4, play, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
      b.addAction(0, "Play", PendingIntent.getActivity(context, e.id.toInt() * 4 + 1, play, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
      val share = Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType(e.mimeType ?: "video/*").putExtra(Intent.EXTRA_STREAM, uri)
          .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        null,
      ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      b.addAction(0, "Share", PendingIntent.getActivity(context, e.id.toInt() * 4 + 2, share, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    }
    notify(context, (10_000 + e.id).toInt(), b.build())
  }

  fun failed(context: Context, e: DownloadEntity) {
    val b = NotificationCompat.Builder(context, CHANNEL_DONE)
      .setSmallIcon(android.R.drawable.stat_notify_error)
      .setContentTitle("Download failed")
      .setContentText("${e.fileName}: ${e.failReason?.message ?: "Download failed"}")
      .setAutoCancel(true)
      .setContentIntent(openDownloads(context))
      .addAction(0, "Retry", serviceAction(context, DownloadService.ACTION_RETRY, e.id))
    if (app.arju.mpvgo.webbrowser.core.LinkRefresh.canRefresh(e)) {
      val refresh = Intent(context, MainActivity::class.java)
        .putExtra(app.arju.mpvgo.webbrowser.core.LinkRefresh.EXTRA_ID, e.id)
        .putExtra(app.arju.mpvgo.webbrowser.core.LinkRefresh.EXTRA_PAGE, e.sourcePageUrl)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
      b.addAction(0, "Refresh link", PendingIntent.getActivity(context, e.id.toInt() * 4 + 3, refresh, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
    } else {
      b.addAction(0, "Open", openDownloads(context))
    }
    notify(context, (10_000 + e.id).toInt(), b.build())
  }

  fun cancel(context: Context, id: Long) {
    context.getSystemService(NotificationManager::class.java).cancel((10_000 + id).toInt())
  }

  /** A shareable/playable Uri for a finished download. */
  fun contentUri(context: Context, e: DownloadEntity): Uri? {
    e.finalUri?.let { return Uri.parse(it) }
    val path = e.finalPath ?: return null
    return runCatching { FileProvider.getUriForFile(context, "${context.packageName}.provider", File(path)) }
      .getOrNull() ?: Uri.fromFile(File(path))
  }

  fun openDownloads(context: Context): PendingIntent =
    PendingIntent.getActivity(
      context,
      7300,
      Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_OPEN_DOWNLOADS, true)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun serviceAction(context: Context, action: String, id: Long = -1): PendingIntent =
    PendingIntent.getService(
      context,
      action.hashCode() + id.toInt(),
      Intent(context, DownloadService::class.java).setAction(action).putExtra(DownloadService.EXTRA_ID, id),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

  private fun notify(context: Context, id: Int, n: Notification) {
    runCatching { context.getSystemService(NotificationManager::class.java).notify(id, n) }
  }
}
