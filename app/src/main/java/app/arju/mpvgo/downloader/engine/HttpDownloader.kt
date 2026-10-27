package app.arju.mpvgo.downloader.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger

/** Callbacks from a running transfer. [onProgress] is frequent; [onCheckpoint] is for persistence. */
interface TransferListener {
  fun onProgress(downloaded: Long, total: Long, activeConnections: Int) {}

  fun onPlan(plan: RangePlan?) {}

  suspend fun onResolved(info: HttpDownloader.Resolved) {}

  suspend fun onCheckpoint(downloaded: Long, total: Long, state: String?) {}
}

/** Thrown when a URL turns out to be an HLS playlist; the caller switches to [HlsDownloader]. */
class SwitchToHls : Exception("HLS playlist")

/** Plain HTTP(S) downloader: probes range support, then fills a [RangePlan] with parallel connections. */
class HttpDownloader(private val client: OkHttpClient) {

  data class Resolved(
    val total: Long,
    val supportsResume: Boolean,
    val mimeType: String?,
    val contentDisposition: String?,
    val finalUrl: String,
  )

  /**
   * Downloads [url] into [target]. [resumeState] is a previous [RangePlan.serialize] (valid only if
   * [knownTotal] matches). Returns when the file is complete.
   */
  suspend fun download(
    url: String,
    headers: Map<String, String>,
    target: File,
    connections: Int,
    knownTotal: Long,
    resumeState: String?,
    limiter: SpeedLimiter,
    listener: TransferListener,
  ) = withContext(Dispatchers.IO) {
    val probe = execute(request(url, headers, "bytes=0-"))
    val resolved: Resolved
    try {
      val code = probe.code
      if (code == 416 && knownTotal > 0 && target.length() == knownTotal) {
        probe.close()
        listener.onCheckpoint(knownTotal, knownTotal, null)
        return@withContext
      }
      if (code !in 200..299) throw DownloadHttpException(code)
      val mime = probe.header("Content-Type")
      if (mime != null && (mime.contains("mpegurl", true) || mime.contains("x-mpegURL", true))) throw SwitchToHls()
      if (mime != null && mime.startsWith("text/html", true)) throw DownloadFailure(FailReason.NotVideo)
      val ranged = code == 206
      val total = if (ranged) {
        probe.header("Content-Range")?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L
      } else {
        probe.body.contentLength()
      }
      resolved = Resolved(total, ranged && total > 0, mime, probe.header("Content-Disposition"), probe.request.url.toString())
      listener.onResolved(resolved)
    } catch (t: Throwable) {
      probe.close()
      throw t
    }

    if (!resolved.supportsResume) {
      // Single stream from the start; reuse the probe response.
      listener.onPlan(null)
      probe.use { streamWhole(it, target, resolved.total, limiter, listener) }
      return@withContext
    }
    probe.close()

    val total = resolved.total
    val plan = resumeState?.takeIf { knownTotal == total && target.exists() }?.let { RangePlan.deserialize(total, it) }
      ?: RangePlan.create(total, connections).also {
        target.parentFile?.mkdirs()
        RandomAccessFile(target, "rw").use { f -> f.setLength(total) }
      }
    listener.onPlan(plan)
    val active = AtomicInteger(0)
    val n = connections.coerceIn(1, 32)
    try {
      coroutineScope {
        val workers = List(n) {
          launch(Dispatchers.IO) {
            RandomAccessFile(target, "rw").use { raf ->
              while (isActive) {
                val part = plan.claim() ?: break
                active.incrementAndGet()
                try {
                  fetchPart(resolved.finalUrl, headers, part, plan, raf, limiter) {
                    listener.onProgress(plan.downloadedBytes(), total, active.get())
                  }
                } finally {
                  plan.release(part)
                  active.decrementAndGet()
                }
              }
            }
          }
        }
        val checkpoints = launch {
          while (isActive) {
            delay(CHECKPOINT_MS)
            listener.onCheckpoint(plan.downloadedBytes(), total, plan.serialize())
          }
        }
        workers.joinAll()
        checkpoints.cancel()
      }
    } finally {
      withContext(NonCancellable) {
        listener.onCheckpoint(plan.downloadedBytes(), total, plan.serialize())
      }
    }
    if (!plan.isComplete()) throw DownloadFailure(FailReason.ServerError)
  }

  private suspend fun fetchPart(
    url: String,
    headers: Map<String, String>,
    part: RangePlan.Part,
    plan: RangePlan,
    raf: RandomAccessFile,
    limiter: SpeedLimiter,
    onChunk: () -> Unit,
  ) {
    if (part.isDone) return
    val response = execute(request(url, headers, "bytes=${part.pos}-${part.end - 1}"))
    val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { response.close() }
    try {
      response.use { r ->
      if (r.code != 206) {
        if (r.code in 200..299) throw DownloadFailure(FailReason.ServerError)
        throw DownloadHttpException(r.code)
      }
      val input = r.body.byteStream()
      val buf = ByteArray(BUFFER)
      while (!part.isDone) {
        currentCoroutineContext().ensureActive()
        val want = minOf(BUFFER.toLong(), part.remaining).toInt()
        val read = input.read(buf, 0, want)
        if (read < 0) break
        // The part may have shrunk (split) while reading.
        val usable = minOf(read.toLong(), part.remaining).toInt()
        if (usable <= 0) break
        limiter.acquire(usable)
        raf.seek(part.pos)
        raf.write(buf, 0, usable)
        plan.advance(part, usable)
        onChunk()
      }
      // Server closed early: the plan keeps the remainder; the retry resumes it.
      if (!part.isDone) throw EOFException("Connection closed early")
      }
    } finally {
      cancelHandle.dispose()
    }
  }

  private suspend fun streamWhole(
    response: Response,
    target: File,
    total: Long,
    limiter: SpeedLimiter,
    listener: TransferListener,
  ) {
    target.parentFile?.mkdirs()
    var written = 0L
    var lastCheckpoint = 0L
    val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { response.close() }
    try {
    target.outputStream().buffered(BUFFER).use { out ->
      val input = response.body.byteStream()
      val buf = ByteArray(BUFFER)
      while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buf)
        if (read < 0) break
        limiter.acquire(read)
        out.write(buf, 0, read)
        written += read
        listener.onProgress(written, total, 1)
        val now = System.currentTimeMillis()
        if (now - lastCheckpoint > CHECKPOINT_MS) {
          lastCheckpoint = now
          listener.onCheckpoint(written, total, null)
        }
      }
    }
    } finally {
      cancelHandle.dispose()
    }
    if (total > 0 && written < total) throw EOFException("Connection closed early")
    listener.onCheckpoint(written, if (total > 0) total else written, null)
  }

  private fun request(url: String, headers: Map<String, String>, range: String?): Request =
    Request.Builder().url(url).apply {
      headers.forEach { (k, v) -> header(k, v) }
      if (range != null) header("Range", range)
      header("Accept-Encoding", "identity")
    }.build()

  private suspend fun execute(request: Request): Response {
    val call: Call = client.newCall(request)
    val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
    return try {
      call.execute()
    } catch (e: Exception) {
      if (!currentCoroutineContext().isActive) throw CancellationException("cancelled").apply { initCause(e) }
      throw e
    } finally {
      handle.dispose()
    }
  }

  companion object {
    const val BUFFER = 64 * 1024
    const val CHECKPOINT_MS = 2000L
  }
}
