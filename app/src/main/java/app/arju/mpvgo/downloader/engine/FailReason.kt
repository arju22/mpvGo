package app.arju.mpvgo.downloader.engine

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Why a download stopped, and whether retrying can help. */
enum class FailReason(val message: String, val retryable: Boolean) {
  LinkExpired("Link expired (403)", false),
  NotFound("File not found (404)", false),
  ServerError("Server error", true),
  NoConnection("No connection", true),
  StorageFull("Storage full", false),
  Drm("Protected video (DRM), can't download", false),
  Live("Live streams can't be downloaded", false),
  NotVideo("The link isn't a video file", false),
  Unknown("Download failed", true),
  ;

  companion object {
    fun fromHttp(code: Int): FailReason = when (code) {
      401, 403, 410 -> LinkExpired
      404 -> NotFound
      408, 429 -> NoConnection
      in 500..599 -> ServerError
      else -> Unknown
    }

    fun fromThrowable(t: Throwable): FailReason = when {
      t is DownloadHttpException -> fromHttp(t.code)
      t is DownloadFailure -> t.reason
      t.isNoSpace() -> StorageFull
      t is UnknownHostException || t is SocketTimeoutException -> NoConnection
      t is IOException -> NoConnection
      else -> Unknown
    }

    private fun Throwable.isNoSpace(): Boolean {
      var c: Throwable? = this
      while (c != null) {
        if (c.message?.contains("ENOSPC") == true || c.message?.contains("No space left", true) == true) return true
        c = c.cause
      }
      return false
    }
  }
}

class DownloadHttpException(val code: Int) : IOException("HTTP $code")

class DownloadFailure(val reason: FailReason) : IOException(reason.message)

/** Retry policy: [MAX_RETRIES] attempts with exponential backoff capped at 30 s. */
object RetryPolicy {
  const val MAX_RETRIES = 10

  fun delayMs(attempt: Int): Long = (1000L shl (attempt - 1).coerceIn(0, 5)).coerceAtMost(30_000L)

  fun shouldRetry(reason: FailReason, attempt: Int): Boolean = reason.retryable && attempt < MAX_RETRIES
}
